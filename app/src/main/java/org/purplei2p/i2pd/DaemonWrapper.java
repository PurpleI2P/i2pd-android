package org.purplei2p.i2pd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class DaemonWrapper {

    private static final String TAG = "i2pd";
    private final AssetManager assetManager;
    private final ConnectivityManager connectivityManager;
    private String i2pdpath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/i2pd";
    private boolean assetsCopied;

    private String getAppLocale() {
        return Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).toLowerCase(); // lower-case system language (like "english")
    }

    public interface StateUpdateListener {
        void daemonStateUpdate(State oldValue, State newValue);
    }

    private final Set<StateUpdateListener> stateUpdateListeners = new HashSet<>();

    public synchronized void addStateChangeListener(StateUpdateListener listener) {
        stateUpdateListeners.add(listener);
    }

    public synchronized void removeStateChangeListener(StateUpdateListener listener) {
        stateUpdateListeners.remove(listener);
    }

    private synchronized void setState(State newState) {
        if (newState == null)
            throw new NullPointerException();

        State oldState = state;

        if (oldState == null)
            throw new NullPointerException();

        if (oldState.equals(newState))
            return;

        state = newState;
        fireStateUpdate1(oldState, newState);
    }

    public synchronized void stopAcceptingTunnels() {
        if (isStartedOkay()) {
            setState(State.gracefulShutdownInProgress);
            I2pdApi.stopAcceptingTunnels();
        }
    }

    public synchronized void startAcceptingTunnels() {
        if (isStartedOkay()) {
            setState(State.startedOkay);
            I2pdApi.startAcceptingTunnels();
        }
    }

    public synchronized void reloadTunnelsConfigs() {
        if (isStartedOkay()) {
            I2pdApi.reloadTunnelsConfigs();
        }
    }

    public int getTransitTunnelsCount() {
        return I2pdApi.getTransitTunnelsCount();
    }

    public enum State {
        uninitialized(R.string.uninitialized),
        starting(R.string.starting),
        startedOkay(R.string.startedOkay),
        startFailed(R.string.startFailed),
        gracefulShutdownInProgress(R.string.gracefulShutdownInProgress),
        stopped(R.string.stopped);

        State(int statusStringResourceId) {
            this.statusStringResourceId = statusStringResourceId;
        }

        private final int statusStringResourceId;

        public int getStatusStringResourceId() {
            return statusStringResourceId;
        }

        public boolean isStartedOkay() {
            return equals(State.startedOkay) || equals(State.gracefulShutdownInProgress);
        }
    }

    private volatile State state = State.uninitialized;

    public State getState() {
        return state;
    }

    public DaemonWrapper(Context ctx, AssetManager assetManager, ConnectivityManager connectivityManager){
        this.assetManager = assetManager;
        this.connectivityManager = connectivityManager;
        setState(State.starting);
        startDaemon(ctx);
    }

    private Throwable lastThrowable;
    private String daemonStartResult = "N/A";

    private void fireStateUpdate1(State oldValue, State newValue) {
        Log.d(TAG, "daemon state change: " + state);
        for (StateUpdateListener listener : stateUpdateListeners) {
            try {
                listener.daemonStateUpdate(oldValue, newValue);
            } catch (Throwable tr) {
                Log.e(TAG, "exception in listener ignored", tr);
            }
        }
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }

    public String getDaemonStartResult() {
        return daemonStartResult;
    }

    public boolean isStartedOkay() {
        return getState().isStartedOkay();
    }

    public synchronized void stopDaemon() {
        if (isStartedOkay()) {
            try {
                I2pdApi.stopDaemon();
            } catch (Throwable tr) {
                Log.e(TAG, "", tr);
            }
            setState(State.stopped);
        }
    }
    public synchronized void startDaemon(Context ctx) {
        if( getState() != State.stopped && getState() != State.starting ) return;
        new Thread(() -> {
            try {
                processAssets();
                //registerNetworkCallback();
            } catch (Throwable tr) {
                lastThrowable = tr;
                setState(State.startFailed);
                return;
            }
            try {
                synchronized (DaemonWrapper.this) {
                    String locale = getAppLocale();
                    Log.i(TAG, "setting webconsole language to " + locale);

                    daemonStartResult = I2pdApi.startDaemon(ctx, i2pdpath, locale);
                    if ("ok".equals(daemonStartResult)) {
                        setState(State.startedOkay);
                    } else
                        setState(State.startFailed);
                }
            } catch (Throwable tr) {
                lastThrowable = tr;
                setState(State.startFailed);
            }
        }, "i2pdDaemonStart").start();
    }

    private void processAssets() {
        File holderFile = new File(i2pdpath, "assets.ready");
        String versionName = BuildConfig.VERSION_NAME; // here will be app version, like 2.XX.XX
        StringBuilder text = new StringBuilder();
        Log.d(TAG, "checking assets");

        if (holderFile.exists()) {
            try (FileReader fileReader = new FileReader(holderFile)) { // if holder file exists, read assets version string
                try (BufferedReader br = new BufferedReader(fileReader)) {
                        String line;

                        while ((line = br.readLine()) != null) {
                            text.append(line);
                        }
                }
            } catch (IOException e) {
                Log.e(TAG, "", e);
            }
        }

        // if version differs from current app version or null, try to delete certificates folder
        if (!text.toString().contains(versionName)) {
            try {
                boolean deleteResult = holderFile.delete();
                if (!deleteResult)
                    Log.e(TAG, "holderFile.delete() returned " + deleteResult + ", absolute path='" + holderFile.getAbsolutePath() + "'");
                File certPath = new File(i2pdpath, "certificates");
                deleteRecursive(certPath);

                // copy assets. If processed file exists, it won't be overwritten
                copyAsset("addressbook");
                copyAsset("certificates");
                copyAsset("tunnels.d");
                copyAsset("i2pd.conf");
                copyAsset("subscriptions.txt");
                copyAsset("tunnels.conf");

                // update holder file about successful copying
                ;
                try (FileWriter writer = new FileWriter(holderFile)) {
                    writer.append(versionName);
                }
            }
            catch (Throwable tr)
            {
                Log.e(TAG,"on assets copying", tr);
            }
        }
    }

    /**
     * Copy the asset at the specified path to this app's data directory. If the
     * asset is a directory, its contents are also copied.
     *
     * @param path
     * Path to asset, relative to app's assets directory.
     */
    private void copyAsset(String path) {
        // If we have a directory, we make it and recurse. If a file, we copy its
        // contents.
        try {
            String[] contents = assetManager.list(path);

            // The documentation suggests that list throws an IOException, but doesn't
            // say under what conditions. It'd be nice if it did so when the path was
            // to a file. That doesn't appear to be the case. If the returned array is
            // null or has 0 length, we assume the path is to a file. This means empty
            // directories will get turned into files.
            if (contents == null || contents.length == 0) {
                copyFileAsset(path);
                return;
            }

            // Make the directory.
            File dir = new File(i2pdpath, path);
            boolean result = dir.mkdirs();
            Log.d(TAG, "dir.mkdirs() returned " + result + " for " + dir);

            // Recurse on the contents.
            for (String entry : contents) {
                copyAsset(path + '/' + entry);
            }
        } catch (IOException e) {
            Log.e(TAG, "ex ignored for path='" + path + "'", e);
        }
    }

    /**
     * Copy the asset file specified by path to app's data directory. Assumes
     * parent directories have already been created.
     *
     * @param path
     * Path to asset, relative to app's assets directory.
     */
    private void copyFileAsset(String path) {
        File file = new File(i2pdpath, path);
        if (!file.exists()) {
            try {
                try (InputStream in = assetManager.open(path)) {
                    try (OutputStream out = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int read = in.read(buffer);
                        while (read != -1) {
                            out.write(buffer, 0, read);
                            read = in.read(buffer);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "", e);
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        boolean deleteResult = fileOrDirectory.delete();
        if (!deleteResult)
            Log.d(TAG, "fileOrDirectory.delete() returned " + deleteResult + ", absolute path='" + fileOrDirectory.getAbsolutePath() + "'");
    }

    private void registerNetworkCallback(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) registerNetworkCallback0();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void registerNetworkCallback0() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        NetworkStateCallbackImpl networkCallback = new NetworkStateCallbackImpl();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static final class NetworkStateCallbackImpl extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            I2pdApi.onNetworkStateChanged(true);
            Log.d(TAG, "NetworkCallback.onAvailable");
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            I2pdApi.onNetworkStateChanged(false);
            Log.d(TAG, " NetworkCallback.onLost");
        }
    }
}

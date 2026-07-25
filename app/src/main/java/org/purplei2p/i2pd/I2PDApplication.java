package org.purplei2p.i2pd;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.Timer;

public class I2PDApplication extends android.app.Application {
    public static final int GRACEFUL_DELAY_MILLIS = 10 * 60 * 1000;
    private static final String TAG = "App";
    public static final Object graceStartedMillis_LOCK = new Object();
    private static I2PDApplication instance = null;

    private static volatile boolean startDaemon = false;
    public static volatile long graceStartedMillis;
    public static volatile Timer gracefulQuitTimer;

    public I2PDApplication() {
        I2PDApplication.instance = this;
    }


    public static synchronized boolean isStartDaemon() {
        return startDaemon;
    }

    public static synchronized void setStartDaemon(boolean startDaemon) {
        I2PDApplication.startDaemon = startDaemon;
    }

//private static final I2PD_JNI jniHolder = new I2PD_JNI();

    private static volatile DaemonWrapper daemonWrapper;
    private String versionName;

    private static volatile boolean mIsBound;


    public synchronized static DaemonWrapper getDaemonWrapper() {
        return daemonWrapper;
    }

    public static void startForegroundService(Context context) {
        Intent serviceIntent = new Intent(context, ForegroundService.class);
        serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static void maybeAutostartForegroundServiceOnBoot(Context context) {
        boolean autostartOnBoot = isAutostartOnBoot(context);
        if(autostartOnBoot) {
            startForegroundService(context);
        }
    }

    public static boolean isAutostartOnBoot(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                org.purplei2p.i2pd.BootUpReceiver.SHARED_PREF_FILE_KEY, MODE_PRIVATE);
        return sharedPref.getBoolean(org.purplei2p.i2pd.BootUpReceiver.AUTOSTART_ON_BOOT, true);
    }

    public static I2PDApplication getInstance() {
        return instance;
    }

    public static String formatGraceTimeRemaining() {
        long remainingSeconds;
        synchronized (graceStartedMillis_LOCK) {
            remainingSeconds = Math.round(Math.max(0, graceStartedMillis + GRACEFUL_DELAY_MILLIS - System.currentTimeMillis()) / 1000.0D);
        }
        long remainingMinutes = (long) Math.floor(remainingSeconds / 60.0D);
        long remSec = remainingSeconds - remainingMinutes * 60;
        return remainingMinutes + ":" + (remSec / 10) + remSec % 10;
    }

    public static Timer getGracefulQuitTimer() {
        return gracefulQuitTimer;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "App.onCreate");
        super.onCreate();
        if(I2PDApplication.isAutostartOnBoot(getApplicationContext())){
            Log.d(TAG, "calling App.setStartDaemon(true)");
            I2PDApplication.setStartDaemon(true);
        }
        if(I2PDApplication.isStartDaemon()){
            Log.d(TAG, "calling App.doBindService()");
            doBindService();
        }
        Log.d(TAG, "calling App.maybeAutostartForegroundServiceOnBoot()");
        maybeAutostartForegroundServiceOnBoot(getApplicationContext());
//        synchronized (this) {
//            if (getDaemonWrapper() == null) {
//                createDaemonWrapper();
//            }
        versionName = BuildConfig.VERSION_NAME;
//            startService(new Intent(this, ForegroundService.class));
//        }
        startService(new Intent(this, ForegroundService.class));
        createDaemonWrapper();

        //daemonWrapper.addStateChangeListener(daemonStateUpdatedListener);
        //daemonStateUpdatedListener.daemonStateUpdate(DaemonWrapper.State.uninitialized, daemon.getState());
        Log.d(TAG, "App.onCreate() leave");
    }

    private void createDaemonWrapper() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        daemonWrapper = new DaemonWrapper(getAssets(), connectivityManager);
        ForegroundService.init(daemonWrapper);
    }

    private synchronized void doBindService() {
        if (mIsBound)
            return;
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this, ForegroundService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private synchronized void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onTerminate() {
        quit();
        super.onTerminate();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            /* This is called when the connection with the service has been
               established, giving us the service object we can use to
               interact with the service.  Because we have bound to a explicit
               service that we know is running in our own process, we can
               cast its IBinder to a concrete class and directly access it. */
            // mBoundService = ((LocalService.LocalBinder)service).getService();

            /* Tell the user about this for our demo. */
            // Toast.makeText(Binding.this, R.string.local_service_connected,
            // Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            /* This is called when the connection with the service has been
               unexpectedly disconnected -- that is, its process crashed.
               Because it is running in our same process, we should never
               see this happen. */
            // mBoundService = null;
            // Toast.makeText(Binding.this, R.string.local_service_disconnected,
            // Toast.LENGTH_SHORT).show();
        }
    };

    public static synchronized void quit() {
        try {
            if (daemonWrapper != null) daemonWrapper.stopDaemon();
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
        }

        try {
            if(instance!=null)instance.doUnbindService();
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "throwable caught and ignored", ex);
            final String message = ex.getMessage();
            if (message!=null && message.startsWith("Service not registered: " + I2PDActivity.class.getName())) {
                Log.i(TAG, "Service not registered exception seems to be normal, not a bug it seems.");
            }
        } catch (Throwable tr) {
            Log.e(TAG, "throwable caught and ignored", tr);
        }
        try {
            Log.d(TAG, "calling fgservice.stop");
            ForegroundService.deinit();
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
        }
        System.exit(0);
    }

    public boolean isPermittedToWriteToExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            Method methodCheckPermission;

            try {
                methodCheckPermission = getClass().getMethod(
                        "checkSelfPermission", String.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            Integer resultObj;

            try {
                resultObj = (Integer) methodCheckPermission.invoke(
                        this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            return resultObj != null && resultObj == PackageManager.PERMISSION_GRANTED;
        }
    }
}

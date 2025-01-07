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

import org.purplei2p.i2pd.receivers.BootUpReceiver;

import java.lang.reflect.Method;

public class Application extends android.app.Application {
    private static final String TAG = "App";

    private static volatile boolean startDaemon = false;

    public static synchronized boolean isStartDaemon() {
        return startDaemon;
    }

    public static synchronized void setStartDaemon(boolean startDaemon) {
        Application.startDaemon = startDaemon;
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
                BootUpReceiver.SHARED_PREF_FILE_KEY, MODE_PRIVATE);
        boolean autostartOnBoot = sharedPref.getBoolean(BootUpReceiver.AUTOSTART_ON_BOOT, true);
        return autostartOnBoot;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "App.onCreate");
        super.onCreate();
        if(Application.isAutostartOnBoot(getApplicationContext())){
            Log.d(TAG, "calling App.setStartDaemon(true)");
            Application.setStartDaemon(true);
        }
        if(Application.isStartDaemon()){
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
        Log.d(TAG, "App.onCreate() leave");
    }

    public void createDaemonWrapper() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        daemonWrapper = new DaemonWrapper(this, getApplicationContext(), getAssets(), connectivityManager);
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

    public synchronized void quit() {
        try {
            if (daemonWrapper != null) daemonWrapper.stopDaemon(null);
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
        }

        try {
            doUnbindService();
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "throwable caught and ignored", ex);
            if (ex.getMessage().startsWith("Service not registered: " + I2PDActivity.class.getName())) {
                Log.i(TAG, "Service not registered exception seems to be normal, not a bug it seems.");
            }
        } catch (Throwable tr) {
            Log.e(TAG, "throwable caught and ignored", tr);
        }
        try {
            Log.d(TAG, "calling fgservice.stop");
            ForegroundService fs = ForegroundService.getInstance();
            if (fs != null) fs.stop();
        } catch (Throwable tr) {
            Log.e(TAG, "", tr);
        }
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

            return resultObj == PackageManager.PERMISSION_GRANTED;
        }
    }
}

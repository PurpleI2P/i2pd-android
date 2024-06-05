package org.purplei2p.i2pd.appscope;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.util.Log;

import org.purplei2p.i2pd.BuildConfig;
import org.purplei2p.i2pd.I2PDActivity;
import org.purplei2p.i2pd.*;

public class App extends Application {
    private static final String TAG = "i2pd.app";

    //private static final I2PD_JNI jniHolder = new I2PD_JNI();

    private static volatile DaemonWrapper daemonWrapper;
    private String versionName;

    private static volatile boolean mIsBound;



    public synchronized static DaemonWrapper getDaemonWrapper() {
        return daemonWrapper;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (this) {
            if (getDaemonWrapper() == null) {
                createDaemonWrapper();
            }
            versionName = BuildConfig.VERSION_NAME;
            doBindService();
            startService(new Intent(this, ForegroundService.class));
        }
    }

    private void createDaemonWrapper() {
        ConnectivityManager  connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        daemonWrapper = new DaemonWrapper(getApplicationContext(), getAssets(), connectivityManager);
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
}

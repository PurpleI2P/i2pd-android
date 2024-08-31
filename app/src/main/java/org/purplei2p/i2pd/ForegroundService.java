package org.purplei2p.i2pd;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import org.purplei2p.i2pd.appscope.App;
import org.purplei2p.i2pd.receivers.BootUpReceiver;

public class ForegroundService extends Service {
    private static final String TAG = "FgService";
    private volatile boolean shown;

    public static ForegroundService getInstance() {
        return instance;
    }

    private static volatile ForegroundService instance;
    private static volatile DaemonWrapper daemon;
    private static final Object initDeinitLock = new Object();

    private final DaemonWrapper.StateUpdateListener daemonStateUpdatedListener =
            (oldValue, newValue) -> updateNotificationText();

    private void updateNotificationText() {
        Log.d(TAG, "FgSvc.updateNotificationText() enter");
        try {
            synchronized (initDeinitLock) {
                if (shown){
                    Log.d(TAG, "FgSvc.updateNotificationText() calling cancelNotification()");
                    cancelNotification();
                }
                if(App.isStartDaemon()){
                    Log.d(TAG, "FgSvc.updateNotificationText() calling showNotification()");
                    showNotification();
                }
            }
        } catch (Throwable tr) {
            Log.e(TAG,"error ignored", tr);
        }
        Log.d(TAG, "FgSvc.updateNotificationText() leave");
    }


    private NotificationManager notificationManager;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private static final int NOTIFICATION_ID = 1;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
    }

    public static void init(DaemonWrapper daemon) {
        ForegroundService.daemon = daemon;
        initCheck();
    }

    private static void initCheck() {
        synchronized (initDeinitLock) {
            if (instance != null && daemon != null) instance.setListener();
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "FgSvc.onCreate()");
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        final App app = (App) getApplication();
        if (daemon == null) daemon = App.getDaemonWrapper();
        if (daemon == null) {
            if(App.isStartDaemon()) {
                Log.d(TAG, "FgSvc.onCreate() calling app.createDaemonWrapper()");
                app.createDaemonWrapper();
            }
            daemon = App.getDaemonWrapper();
        }
        instance = this;
        initCheck();
        Log.d(TAG, "FgSvc.onCreate() leave");
    }

    private void setListener() {
        final DaemonWrapper dw = daemon;
        if (dw != null) dw.addStateChangeListener(daemonStateUpdatedListener);
        updateNotificationText();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received start id " + startId + ": " + intent);
        readFlags(flags);
        return START_STICKY;
    }
    private void readFlags(int flags) {
        if ((flags&START_FLAG_REDELIVERY) == START_FLAG_REDELIVERY)
            Log.d(TAG,"START_FLAG_REDELIVERY");
        if ((flags&START_FLAG_RETRY) == START_FLAG_RETRY)
            Log.d(TAG,"START_FLAG_RETRY");
    }
    @Override
    public void onDestroy() {
        stop();
    }

    public void stop() {
        Log.e(TAG,"stop() enter", new Throwable("dumpstack"));
        cancelNotification();
        deinitCheck();
        instance = null;
        Log.d(TAG,"stop() leave");
    }

    public static void deinit() {
        deinitCheck();
    }

    private static void deinitCheck() {
        synchronized (initDeinitLock) {
            if (daemon != null && instance != null)
                daemon.removeStateChangeListener(instance.daemonStateUpdatedListener);
        }
    }

    private void cancelNotification() {
        Log.d(TAG, "FgSvc.cancelNotification()");
        synchronized (initDeinitLock) {
            notificationManager.cancel(NOTIFICATION_ID);
            stopForeground(true);
            shown = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        Log.d(TAG, "FgSvc.showNotification() enter");
        synchronized (initDeinitLock) {
            Log.d(TAG, "FgSvc.showNotification(): daemon="+daemon);
            if (daemon != null) {
                CharSequence text = getText(daemon.getState().getStatusStringResourceId());
                Log.d(TAG, "FgSvc.showNotification(): text="+text);

                // The PendingIntent to launch our activity if the user selects this notification
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, I2PDPermsAskerActivity.class),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? FLAG_IMMUTABLE : 0);

                // on old Android, the channel id is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";

                // Set the info for the views that show in the notification panel.
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_notification_icon) // the status icon
                        .setPriority(Notification.PRIORITY_DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    builder = builder.setCategory(Notification.CATEGORY_SERVICE);
                Notification notification = builder
                        .setTicker(text) // the status text
                        .setWhen(System.currentTimeMillis()) // the time stamp
                        .setContentTitle(getText(R.string.app_name)) // the label of the entry
                        .setContentText(text) // the contents of the entry
                        .setContentIntent(contentIntent) // The intent to send when the entry is clicked
                        .build();

                // Send the notification.
                //mNM.notify(NOTIFICATION, notification);
                Log.d(TAG, "FgSvc.showNotification(): calling startForeground()");
                startForeground(NOTIFICATION_ID, notification);
                shown = true;
            }
        }
        Log.d(TAG, "FgSvc.showNotification() leave");
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private synchronized String createNotificationChannel() {
        String channelId = getString(R.string.app_name);
        CharSequence channelName = getString(R.string.i2pd_service);
        NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        //chan.setLightColor(Color.PURPLE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (service != null) service.createNotificationChannel(chan);
        else Log.e(TAG, "error: NOTIFICATION_SERVICE is null, haven't called createNotificationChannel");
        return channelId;
    }
}

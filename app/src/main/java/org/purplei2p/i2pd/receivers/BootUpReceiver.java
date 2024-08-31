package org.purplei2p.i2pd.receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
//import org.purplei2p.i2pd.ForegroundService;
//ToDo:* fix^^^ change to service, not on window on start.
import org.purplei2p.i2pd.ForegroundService;
import org.purplei2p.i2pd.I2PDPermsAskerActivity;

import java.io.File;

import androidx.core.content.ContextCompat;

public class BootUpReceiver extends BroadcastReceiver {

    public static final String AUTOSTART_ON_BOOT = "autostart_on_boot";
    public static final String SHARED_PREF_FILE_KEY = BootUpReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                SHARED_PREF_FILE_KEY, Context.MODE_PRIVATE);
        boolean autostartOnBoot = sharedPref.getBoolean(AUTOSTART_ON_BOOT, true);
        if(autostartOnBoot) {
            Intent serviceIntent = new Intent(context, ForegroundService.class);
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}


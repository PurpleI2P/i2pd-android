package org.purplei2p.i2pd.receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
//import org.purplei2p.i2pd.ForegroundService;
//ToDo:* fix^^^ change to service, not on window on start.
import org.purplei2p.i2pd.ForegroundService;
import org.purplei2p.i2pd.I2PDPermsAskerActivity;

import java.io.File;

import static org.purplei2p.i2pd.SettingsActivity.onBootFileName;

import androidx.core.content.ContextCompat;

public class BootUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        File onBoot = new File(context.getApplicationContext().getFilesDir(), onBootFileName);
        if(onBoot.exists()) {
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


package org.purplei2p.i2pd.receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
//import org.purplei2p.i2pd.ForegroundService;
//ToDo:* fix^^^ change to service, not on window on start.
import org.purplei2p.i2pd.I2PDPermsAskerActivity;

import java.io.File;

import static org.purplei2p.i2pd.SettingsActivity.ON_BOOT_FILE_NAME;

public class BootUpReceiver extends BroadcastReceiver {
    private static final String TAG = "i2pdBUR";
    @Override
    public void onReceive(Context context, Intent intent) {
        /* todo: disable the autostart? */
        /*
            Warning: This broadcast receiver declares an intent-filter for a protected broadcast action string,
            which can only be sent by the system, not third-party applications. However, the receiver's `onReceive`
            method does not appear to call `getAction` to ensure that the received Intent's action string matches
            the expected value, potentially making it possible for another actor to send a spoofed intent with
            no action string or a different action string and cause undesired behavior.
        */
        Log.d(TAG, "BootUpReceiver: actionString=='" + intent.getAction() + "'");
        File onBoot =
                new File(
                        context.getApplicationContext().getCacheDir().getAbsolutePath()
                                + ON_BOOT_FILE_NAME);
        if(onBoot.exists()) {
            Intent i = new Intent(context, I2PDPermsAskerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }

    }
}


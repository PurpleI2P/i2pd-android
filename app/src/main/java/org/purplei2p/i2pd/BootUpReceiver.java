package org.purplei2p.i2pd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootUpReceiver extends BroadcastReceiver {

    public static final String AUTOSTART_ON_BOOT = "autostart_on_boot";
    public static final String SHARED_PREF_FILE_KEY =
            BootUpReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (I2PDApplication.isAutostartOnBoot(context)) I2PDApplication.setStartDaemon(true);
        I2PDApplication.maybeAutostartForegroundServiceOnBoot(context);
    }
}

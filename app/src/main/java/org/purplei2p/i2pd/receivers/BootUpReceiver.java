package org.purplei2p.i2pd.receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.purplei2p.i2pd.I2PDPermsAskerActivity;

public class BootUpReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        /* todo: disable the autostart? */
        Intent i = new Intent(context, I2PDPermsAskerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}


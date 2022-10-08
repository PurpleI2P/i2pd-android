package org.purplei2p.i2pd;

import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkStateChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "i2pdNSCR";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "Network state change: onReceive entered");
        try {
            /*
                Warning: This broadcast receiver declares an intent-filter for a protected broadcast action string,
                which can only be sent by the system, not third-party applications. However, the receiver's `onReceive`
                method does not appear to call `getAction` to ensure that the received Intent's action string matches
                the expected value, potentially making it possible for another actor to send a spoofed intent with
                no action string or a different action string and cause undesired behavior.
            */
            Log.d(TAG, "Network state change: actionString=='" + intent.getAction() + "'");
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();

            I2PD_JNI.onNetworkStateChanged(isConnected);
        } catch (Throwable tr) {
            Log.e(TAG, "exc", tr);
        }
    }
}

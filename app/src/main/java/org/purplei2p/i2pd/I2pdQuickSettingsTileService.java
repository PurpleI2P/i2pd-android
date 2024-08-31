package org.purplei2p.i2pd;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;
import android.util.Log;

import android.annotation.TargetApi;
import android.os.Build;

/**
 * https://developer.android.com/develop/ui/views/quicksettings-tiles
 */
@TargetApi(Build.VERSION_CODES.N)
public class I2pdQuickSettingsTileService extends TileService {
    private static final String TAG = "TileService";
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "QSTile clicked.");

        try {
            Intent intent = new Intent(getApplicationContext(), I2PDPermsAskerActivity.class);
            if (Build.VERSION.SDK_INT >= 28) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            //start the activity & collapse the quick settings pane
            if (Build.VERSION.SDK_INT < 34) { //Android 14, UPSIDE_DOWN_CAKE
                startActivityAndCollapse(intent);
            } else {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,0, intent, PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pendingIntent);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Error while handling qs_tile click", e);
        }
    }
}

package org.purplei2p.i2pd;

import android.content.Intent;
import android.service.quicksettings.TileService;
import android.util.Log;

import android.annotation.TargetApi;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.N)
public class I2pdQuickSettingsTileService extends TileService {
    private static final String TAG = "TileService";
    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "Tile clicked.");

        try {
            Intent intent = new Intent(this, I2PDPermsAskerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting ForegroundService", e);
        }
    }

}

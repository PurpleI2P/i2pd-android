package org.purplei2p.i2pd;

import android.content.Intent;
import android.service.quicksettings.TileService;
import android.util.Log;

import android.annotation.TargetApi;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.N)
public class I2PDQuickSettingsTileService extends TileService {

    private static final String TAG = "MyQSTileService";
    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "Tile clicked.");

        try {
            // Add the FLAG_ACTIVITY_NEW_TASK flag
            Intent intent = new Intent(this, I2PDActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivityAndCollapse(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting ForegroundService", e);
        }
    }



    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "Tile started listening.");
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "Tile stopped listening.");
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile added.");
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.d(TAG, "Tile removed.");
    }
}

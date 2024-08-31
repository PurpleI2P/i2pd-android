package org.purplei2p.i2pd;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;


import java.io.File;
import java.util.List;
import java.util.Objects;


//import org.purplei2p.i2pd.iniedotr.IniEditor;

public class SettingsActivity extends Activity {
    private String TAG = "Settings";
    /**
     * just file, empty, if exist the do autostart, if not then no.
     */
    public static String onBootFileName = "onBoot";

    //https://gist.github.com/chandruark/3165a5ee3452f2b9ec7736cf1b4c5ea6
    private void maybeStartManufacturerSpecificBootupPermissionManagerActivity() {
        try {
            Intent intent = new Intent();
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
            switch (manufacturer) {
                case "xiaomi":
                    intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    break;
                case "oppo":
                    intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                    break;
                case "vivo":
                    intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                    break;
                case "Letv":
                    intent.setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"));
                    break;
                case "Honor":
                    intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                    break;
                case "oneplus":
                    intent.setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                    break;
            }

            List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (!list.isEmpty()) {
                startActivity(intent);
            }
        } catch (Throwable e) {
            Log.e(TAG,"", e);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        final ActionBar actionBar = getActionBar();
        if(actionBar!=null)actionBar.setDisplayHomeAsUpEnabled(true);
        Switch autostartSwitch = findViewById(R.id.autostart_enable);
        Button openPreferencesButton = findViewById(R.id.OpenPreferences);
        File prefsDir = getApplicationContext().getFilesDir();
        File onBoot = new File(prefsDir, onBootFileName);
        openPreferencesButton.setOnClickListener(view -> {
            Intent intent = new Intent(SettingsActivity.this, MainPreferenceActivity.class);
            startActivity(intent);
        });
        autostartSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (isChecked) {
                if (!onBoot.exists()) {
                    maybeStartManufacturerSpecificBootupPermissionManagerActivity();
                    try {
                        if (!onBoot.createNewFile())
                            Log.d(TAG, "Can't create new file "+onBoot.getAbsolutePath());
                    } catch (Throwable e) {
                        Log.e(TAG,"",e);
                    }
                }
            } else {
                if (onBoot.exists())
                    if (!onBoot.delete())
                        Log.d(TAG, "Can't delete file "+onBoot.getAbsolutePath());
            }
        });
        autostartSwitch.setChecked(onBoot.exists());
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }
}

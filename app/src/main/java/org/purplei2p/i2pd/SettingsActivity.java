package org.purplei2p.i2pd;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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


import org.purplei2p.i2pd.receivers.BootUpReceiver;

import java.io.File;
import java.util.List;
import java.util.Objects;


//import org.purplei2p.i2pd.iniedotr.IniEditor;

public class SettingsActivity extends Activity {
    private final static String TAG = "Settings";

    /** https://gist.github.com/chandruark/3165a5ee3452f2b9ec7736cf1b4c5ea6 */
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
        openPreferencesButton.setOnClickListener(view -> {
            Intent intent = new Intent(SettingsActivity.this, MainPreferenceActivity.class);
            startActivity(intent);
        });
        final SharedPreferences sharedPrefBootUp = getApplicationContext().getSharedPreferences(
                BootUpReceiver.SHARED_PREF_FILE_KEY, Context.MODE_PRIVATE);
        autostartSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            boolean autostartOnBootPrevValue = sharedPrefBootUp.getBoolean(BootUpReceiver.AUTOSTART_ON_BOOT, true);
            SharedPreferences.Editor editor = sharedPrefBootUp.edit();
            editor.putBoolean(BootUpReceiver.AUTOSTART_ON_BOOT, isChecked);
            editor.apply();
            if (isChecked && !autostartOnBootPrevValue) {
                maybeStartManufacturerSpecificBootupPermissionManagerActivity();
            }
        });
        boolean autostartOnBoot = sharedPrefBootUp.getBoolean(BootUpReceiver.AUTOSTART_ON_BOOT, true);
        autostartSwitch.setChecked(autostartOnBoot);
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

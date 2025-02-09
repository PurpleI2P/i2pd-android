package org.purplei2p.i2pd;

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

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class SettingsActivity extends Activity {
    public static String onBootFileName = "/onBoot"; // just file, empty, if exist the do autostart, if not then no.
    private String TAG = "i2pdSrvcSettings";
    private File cacheDir;

    // https://gist.github.com/chandruark/3165a5ee3452f2b9ec7736cf1b4c5ea6
    private void addAutoStartupSwitch() {
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
                case "letv":
                    intent.setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"));
                    break;
                case "honor":
                    intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                    break;
                case "oneplus":
                    intent.setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                    break;
            }

            List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() > 0) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e("exceptionAutostarti2pd", String.valueOf(e));
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, 232);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(true);

        Switch autostart_switch = findViewById(R.id.autostart_enable);
        Button openPreferences = findViewById(R.id.OpenPreferences);
        Button openRawPreferences = findViewById(R.id.open_raw_preferences);
        Button openRawTunnels = findViewById(R.id.open_raw_tunnel);

        cacheDir = getApplicationContext().getCacheDir();
        File onBoot = new File(cacheDir.getAbsolutePath() + onBootFileName);

        openPreferences.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SettingsActivity.this, MainPreferenceActivity.class);
                startActivity(intent);
            }
        });

        openRawPreferences.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File i2pdConf = new File(getApplicationContext().getFilesDir(), "i2pd/i2pd.conf");
                openFileExternally(i2pdConf);
            }
        });

        openRawTunnels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File tunnelsConf = new File(getApplicationContext().getFilesDir(), "i2pd/tunnels.conf");
                openFileExternally(tunnelsConf);
            }
        });

        autostart_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if (isChecked) {
                    if (!onBoot.exists()) {
                        requestPermission();
                        addAutoStartupSwitch();

                        try {
                            if (!onBoot.createNewFile())
                                Log.d(TAG, "Cant create new file on: " + onBoot.getAbsolutePath());
                        } catch (Exception e) {
                            Log.e(TAG, "error: " + e.toString());
                        }
                    }
                } else {
                    if (onBoot.exists())
                        onBoot.delete();
                }
            }
        });
        if (onBoot.exists())
            autostart_switch.setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    private void openFileExternally(File file) {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + file.getAbsolutePath());
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        Uri fileUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );
        } else {
            fileUri = Uri.fromFile(file);
        }

        intent.setDataAndType(fileUri, "text/plain");

        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        if (!activities.isEmpty()) {
            startActivity(intent);
        } else {
            Log.e(TAG, "No app found to open text files.");
        }
    }
}

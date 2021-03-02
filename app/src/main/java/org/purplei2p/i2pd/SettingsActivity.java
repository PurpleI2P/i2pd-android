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
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import java.io.File;
import java.util.List;


import org.purplei2p.i2pd.iniedotr.IniEditor;
public class SettingsActivity extends Activity {
    protected IniEditor iniedit = new IniEditor();
    private String dataDir = DaemonWrapper.getDataDir();//for inieditor
    private String TAG = "i2pdSrvcSettings";
    private File cacheDir;
    public static String onBootFileName="/onBoot"; // just file, empty, if exist the do autostart, if not then no.

    //https://gist.github.com/chandruark/3165a5ee3452f2b9ec7736cf1b4c5ea6
    private void addAutoStartupswitch() {

        try {
            Intent intent = new Intent();
            String manufacturer = android.os.Build.MANUFACTURER .toLowerCase();

            switch (manufacturer){
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
                    intent.setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListAct‌​ivity"));
                    break;
            }

            List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if  (list.size() > 0) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e("exceptionAutostarti2pd" , String.valueOf(e));
        }

    }
    //@Override
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, 232);
            } else {
                //Permission Granted-System will work
            }
        }
    }
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        cacheDir = getApplicationContext().getCacheDir();
        setContentView(R.layout.activity_settings);
        Switch autostart_switch = findViewById(R.id.autostart_enable);
        File onBoot= new File( cacheDir.getAbsolutePath()+onBootFileName);
        autostart_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if(isChecked){
                    if( !onBoot.exists() ) {
                        requestPermission();
                        addAutoStartupswitch();
                        try {
                            if( !onBoot.createNewFile()) Log.d(TAG, "Cant create new wile on: "+onBoot.getAbsolutePath());
                        } catch (Exception e) {
                            Log.d(TAG, "error: " + e.toString());
                        }
                    }
                }else{
                    if( onBoot.exists() )
                        onBoot.delete();
                }
            }
        });
        if(onBoot.exists()) autostart_switch.setChecked(true);
    }
}

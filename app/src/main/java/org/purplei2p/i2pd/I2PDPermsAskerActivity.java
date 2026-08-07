package org.purplei2p.i2pd;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Method;

//dangerous perms, per https://developer.android.com/guide/topics/permissions/normal-permissions.html :
//android.permission.WRITE_EXTERNAL_STORAGE
public class I2PDPermsAskerActivity extends Activity {

    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 0;
    private Button button_request_write_ext_storage_perms;
    private TextView textview_retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //if less than Android 6, no runtime perms req system present
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startMainActivity();
            return;
        }


        setContentView(R.layout.activity_perms_asker);
        button_request_write_ext_storage_perms = (Button) findViewById(R.id.button_request_write_ext_storage_perms);
        textview_retry = (TextView) findViewById(R.id.textview_retry);

        button_request_write_ext_storage_perms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                request_write_ext_storage_perms();
            }
        });
        request_write_ext_storage_perms();
    }

    private void request_write_ext_storage_perms() {

        textview_retry.setVisibility(TextView.GONE);
        button_request_write_ext_storage_perms.setVisibility(Button.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(!Environment.isExternalStorageManager()) {
                showExplanation();
            } else {
                startMainActivity();
            }
        } else {
            Method methodCheckPermission;
            Method method_shouldShowRequestPermissionRationale;
            Method method_requestPermissions;

            try {
                methodCheckPermission = getClass().getMethod("checkSelfPermission", String.class);
                method_shouldShowRequestPermissionRationale =
                        getClass().getMethod("shouldShowRequestPermissionRationale", String.class);
                method_requestPermissions =
                        getClass().getMethod("requestPermissions", String[].class, int.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            Integer resultObj;

            try {
                resultObj = (Integer) methodCheckPermission.invoke(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            if (resultObj != PackageManager.PERMISSION_GRANTED) {
                // Should we show an explanation?
                Boolean aBoolean;
                try {
                    aBoolean = (Boolean) method_shouldShowRequestPermissionRationale.invoke(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (aBoolean) {

                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.

                    showExplanation();

                } else {
                    // No explanation needed, we can request the permission.
                    try {
                        method_requestPermissions.invoke(this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                PERMISSION_WRITE_EXTERNAL_STORAGE);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            } else startMainActivity();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
        String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    startMainActivity();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    textview_retry.setText(R.string.permDenied);
                    textview_retry.setVisibility(TextView.VISIBLE);
                    button_request_write_ext_storage_perms.setVisibility(Button.VISIBLE);
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    private void startMainActivity() {
        startActivity(new Intent(this, I2PDActivity.class));
        finish();
    }

    private static final int SHOW_EXPLANATION_REQUEST = 1;  // The request code
    private static final int APP_STORAGE_ACCESS_REQUEST_CODE = 2;

    private void showExplanation() {
        Intent intent = new Intent(this, I2PDPermsExplanationActivity.class);
        startActivityForResult(intent, SHOW_EXPLANATION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check which request we're responding to and make sure the request was successful
        if (requestCode == SHOW_EXPLANATION_REQUEST && resultCode == RESULT_OK) {
            // Request the permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intentManageAccess = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                startActivityForResult(intentManageAccess, APP_STORAGE_ACCESS_REQUEST_CODE);
            } else {
                Method method_requestPermissions;
                try {
                    method_requestPermissions =
                            getClass().getMethod("requestPermissions", String[].class, int.class);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
                try {
                    method_requestPermissions.invoke(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_WRITE_EXTERNAL_STORAGE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } else if (requestCode == APP_STORAGE_ACCESS_REQUEST_CODE && resultCode == RESULT_OK) {
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startMainActivity();
            } else {
                textview_retry.setText(R.string.permDenied);
                textview_retry.setVisibility(TextView.VISIBLE);
                button_request_write_ext_storage_perms.setVisibility(Button.VISIBLE);

                finish();
            }
        } else {
            finish(); // close the app
        }
	}
    }
}

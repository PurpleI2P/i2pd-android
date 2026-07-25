package org.purplei2p.i2pd;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.widget.Button;

public class I2PDPermissionsExplanationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions_explanation);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.setHomeButtonEnabled(false);
        Button button_ok = findViewById(R.id.button_ok);
        button_ok.setOnClickListener(view -> returnFromActivity());
    }

    private void returnFromActivity() {
        Intent intent = new Intent();
        Activity parent = getParent();
        if (parent == null) {
            setResult(Activity.RESULT_OK, intent);
        } else {
            parent.setResult(Activity.RESULT_OK, intent);
        }
        finish();
    }

}

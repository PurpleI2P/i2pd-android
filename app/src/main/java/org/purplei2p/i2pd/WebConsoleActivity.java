package org.purplei2p.i2pd;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.Objects;

public class WebConsoleActivity extends Activity {
    public static final String TAG = "webconsole";
    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_web_console);

            Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(true);

            webView = findViewById(R.id.webconsole);
            webView.setWebViewClient(new WebViewClient());

            final WebSettings webSettings = webView.getSettings();
            webSettings.setBuiltInZoomControls(true);
            webSettings.setJavaScriptEnabled(false);
            webView.loadUrl(I2PD_JNI.getWebConsAddr());

            swipeRefreshLayout = findViewById(R.id.swipe);
            swipeRefreshLayout.setOnRefreshListener(() -> {
                try {
                    swipeRefreshLayout.setRefreshing(true);
                    new Handler().post(() -> {
                        try {
                            webView.reload();
                            swipeRefreshLayout.setRefreshing(false);
                        }catch(Throwable tr){
                            Log.e(TAG,"", tr);
                        }
                    });
                }catch(Throwable tr){
                    Log.e(TAG,"", tr);
                }
            });
        }catch(Throwable tr){
            Log.e(TAG,"", tr);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        }catch(Throwable tr){
            Log.e(TAG,"", tr);
        }
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

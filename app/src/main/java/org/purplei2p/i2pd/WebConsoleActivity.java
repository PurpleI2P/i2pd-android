package org.purplei2p.i2pd;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.Objects;
import java.util.Timer;

public class WebConsoleActivity extends Activity {
    private static final String TAG="WebConsole";
    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String url = "http://localhost:7070/";
        setContentView(R.layout.activity_web_console);

        Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(true);

        webView = findViewById(R.id.webconsole);
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if(url.equals(failingUrl))
                    new Thread(() -> {
                        try {
                            Thread.sleep(100);
                            new Handler().post(() -> {
                                webView.reload();
                            });
                        }catch(Throwable e){
                            Log.e(TAG, "", e);
                        }
                    }, "timer-web-err-thread").start();
            }
        };
        webView.setWebViewClient(webViewClient);

        final WebSettings webSettings = webView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setJavaScriptEnabled(false);
        webView.loadUrl(url/*I2PD_JNI.getWebConsAddr()*/);

        swipeRefreshLayout = findViewById(R.id.swipe);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(true);
            new Handler().post(() -> {
                swipeRefreshLayout.setRefreshing(false);
                webView.reload();
            });
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
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

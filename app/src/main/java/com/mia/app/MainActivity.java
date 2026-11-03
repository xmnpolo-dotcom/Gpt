package com.mia.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.net.Uri;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 13, 18));
        getWindow().setNavigationBarColor(Color.rgb(12, 13, 18));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 13, 18));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(12, 13, 18));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("majestic-baklava-5a712f.netlify.app") || host.endsWith(".netlify.app"))) return false;
                if ("https".equals(uri.getScheme())) {
                    try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(-1, 5);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(progress, bar);
        setContentView(root);
        if (state == null) webView.loadUrl("https://majestic-baklava-5a712f.netlify.app/");
        else webView.restoreState(state);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        webView.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}

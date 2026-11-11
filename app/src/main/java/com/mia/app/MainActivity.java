package com.mia.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.view.Window;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final String HOME = "https://majestic-baklava-5a712f.netlify.app/";
    private static final int FILE_CHOOSER = 4101;
    private static final int WEB_PERMISSION = 4102;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,11,16));
        getWindow().setNavigationBarColor(Color.rgb(10,11,16));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(10,11,16));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Fetch the live Netlify deployment instead of serving a stale cached page.
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("majestic-baklava-5a712f.netlify.app".equalsIgnoreCase(u.getHost())) return false;
                if ("https".equalsIgnoreCase(u.getScheme())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                    return true;
                }
                return false;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) view.loadDataWithBaseURL(null,
                    "<meta name='viewport' content='width=device-width,initial-scale=1'><body style='background:#0a0b10;color:white;font:16px sans-serif;padding:28px'><h2>No se pudo cargar MIA</h2><p>Comprueba tu conexión y vuelve a intentarlo.</p><button onclick='location.reload()' style='padding:12px 20px'>Reintentar</button></body>",
                    "text/html", "UTF-8", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }
        });
        setContentView(web, new ViewGroup.LayoutParams(-1,-1));
        if (state == null) web.loadUrl(HOME);
        else web.restoreState(state);
    }

    private void handleWebPermission(PermissionRequest request) {
        ArrayList<String> needed = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) &&
                Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) &&
                Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.CAMERA);
        }
        if (needed.isEmpty()) { request.grant(request.getResources()); return; }
        pendingPermission = request;
        requestPermissions(needed.toArray(new String[0]), WEB_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == WEB_PERMISSION && pendingPermission != null) {
            boolean ok = true;
            for (int result : results) if (result != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingPermission.grant(pendingPermission.getResources());
            else pendingPermission.deny();
            pendingPermission = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.reload();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (pendingPermission != null) { pendingPermission.deny(); pendingPermission = null; }
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
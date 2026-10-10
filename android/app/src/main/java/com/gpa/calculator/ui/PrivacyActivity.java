package com.gpa.calculator.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ApiClient;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.databinding.ActivityPrivacyBinding;

/**
 * Shows the privacy policy inside the app. The text is the page managed in the admin panel
 * (backend /privacy, or the admin's custom privacy URL); links to other sites open in the browser.
 */
public class PrivacyActivity extends BaseActivity {
    private ActivityPrivacyBinding b;
    private String policyUrl;
    private boolean failed;

    /** Admin-set URL, otherwise this backend's /privacy page. */
    public static String policyUrl(android.content.Context c) {
        String custom = ConfigStore.get(c).config().app.privacy_url;
        return custom != null && !custom.trim().isEmpty() ? custom.trim() : ApiClient.baseUrl() + "privacy";
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPrivacyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        policyUrl = policyUrl(this);

        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.toolbar.inflateMenu(R.menu.menu_privacy);
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open_browser) { openExternal(Uri.parse(policyUrl)); return true; }
            return false;
        });
        padBottomInset(b.content);

        WebSettings ws = b.web.getSettings();
        ws.setJavaScriptEnabled(false);          // the policy page is plain HTML
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setDomStorageEnabled(false);
        ws.setSupportZoom(false);
        b.web.setBackgroundColor(0);             // no white flash in dark mode; the page follows the app theme
        b.web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                Uri home = Uri.parse(policyUrl);
                boolean samePage = u.getHost() != null && u.getHost().equalsIgnoreCase(home.getHost())
                        && u.getPath() != null && u.getPath().startsWith(home.getPath() == null ? "/" : home.getPath());
                if (samePage) return false;      // stay in the app
                openExternal(u);                 // partner policies, mailto: links, etc.
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                failed = false;
                b.progress.setVisibility(View.VISIBLE);
            }

            @Override public void onPageFinished(WebView view, String url) {
                b.progress.setVisibility(View.GONE);
                if (!failed) {
                    b.error.setVisibility(View.GONE);
                    b.web.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse r) {
                if (request.isForMainFrame() && r.getStatusCode() >= 400) showError();
            }
        });
        b.btnRetry.setOnClickListener(v -> load());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (b.web.canGoBack()) b.web.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        Analytics.get(this).log("privacy_open");
        load();
    }

    private void load() {
        b.error.setVisibility(View.GONE);
        b.web.setVisibility(View.VISIBLE);
        b.web.loadUrl(policyUrl);
    }

    private void showError() {
        failed = true;
        b.progress.setVisibility(View.GONE);
        b.web.setVisibility(View.INVISIBLE);
        b.error.setVisibility(View.VISIBLE);
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        if (b != null) {
            b.web.stopLoading();
            b.web.destroy();
        }
        super.onDestroy();
    }
}

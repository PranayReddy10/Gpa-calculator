package com.gpa.calculator.ui.shop;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gpa.calculator.R;
import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.databinding.ActivityShopCheckoutBinding;
import com.gpa.calculator.ui.BaseActivity;

import java.util.List;

/**
 * The shop's own product page inside the app, so customers can upload their photos, add to cart
 * and check out without leaving. Plain web links stay here; UPI / payment-app / phone / mail
 * links open the right app. No JavaScript bridge is exposed to the page.
 */
public class ShopCheckoutActivity extends BaseActivity implements AdsManager.NoAppOpenAd {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private ActivityShopCheckoutBinding b;
    private String startUrl;
    @Nullable private ValueCallback<Uri[]> pendingFiles;
    private boolean failed;

    /** Android photo picker (no storage permission needed), one or several photos. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickOne =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> deliverFiles(uri == null ? null : new Uri[]{uri}));
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMany =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10), uris -> deliverFiles(toArray(uris)));
    /** Any other file type the page asks for. */
    private final ActivityResultLauncher<Intent> pickOther =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    r -> deliverFiles(WebChromeClient.FileChooserParams.parseResult(r.getResultCode(), r.getData())));

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShopCheckoutBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        startUrl = getIntent().getStringExtra(EXTRA_URL);
        if (startUrl == null || !startUrl.startsWith("https://")) { finish(); return; }
        padBottomInset(b.content);

        String shopName = ConfigStore.get(this).config().shop.name;
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        b.toolbar.setTitle(title != null && !title.isEmpty() ? title : shopName);
        b.toolbar.setSubtitle(Uri.parse(startUrl).getHost());
        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.toolbar.inflateMenu(R.menu.menu_shop_checkout);
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_reload) { load(b.web.getUrl() != null ? b.web.getUrl() : startUrl); return true; }
            if (item.getItemId() == R.id.action_open_browser) {
                ShopNav.openExternal(this, Uri.parse(b.web.getUrl() != null ? b.web.getUrl() : startUrl));
                return true;
            }
            return false;
        });

        // Cart, login and payment pages need cookies, including the payment gateway's.
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(b.web, true);

        WebSettings ws = b.web.getSettings();
        ws.setJavaScriptEnabled(true);           // cart, photo upload and checkout need it
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);          // photos chosen with the picker are content:// files
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSupportMultipleWindows(false);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setUserAgentString(ws.getUserAgentString() + " GPACalculatorApp");

        b.web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(java.util.Locale.ROOT);
                if (scheme.equals("https") || scheme.equals("http")) return false; // shop + payment pages
                openOutside(u, request.getUrl().toString());
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
                CookieManager.getInstance().flush();
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });
        b.web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                b.progress.setProgressCompat(newProgress, true);
            }

            @Override public void onReceivedTitle(WebView view, String t) {
                if (t != null && !t.isEmpty() && !t.startsWith("http")) b.toolbar.setTitle(t);
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return chooseFiles(callback, params);
            }
        });
        b.btnRetry.setOnClickListener(v -> load(b.web.getUrl() != null ? b.web.getUrl() : startUrl));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (b.web.canGoBack()) b.web.goBack();
                else finish();
            }
        });

        if (savedInstanceState != null && b.web.restoreState(savedInstanceState) != null) return;
        if (savedInstanceState == null) {
            Toast.makeText(this, getString(R.string.shop_checkout_hint, shopName), Toast.LENGTH_LONG).show();
        }
        load(startUrl);
    }

    private void load(String url) {
        b.error.setVisibility(View.GONE);
        b.web.setVisibility(View.VISIBLE);
        b.web.loadUrl(url);
    }

    private void showError() {
        failed = true;
        b.progress.setVisibility(View.GONE);
        b.web.setVisibility(View.INVISIBLE);
        b.error.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ photo upload
    private boolean chooseFiles(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (pendingFiles != null) pendingFiles.onReceiveValue(null); // never leave the page waiting
        pendingFiles = callback;
        boolean multiple = params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        try {
            if (acceptsOnlyImages(params.getAcceptTypes())) {
                PickVisualMediaRequest req = new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build();
                if (multiple) pickMany.launch(req); else pickOne.launch(req);
            } else {
                pickOther.launch(params.createIntent());
            }
            return true;
        } catch (Exception e) {
            pendingFiles = null;
            Toast.makeText(this, R.string.shop_photo_pick_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** True when the page's file input only takes pictures (or does not say), so the photo picker fits. */
    static boolean acceptsOnlyImages(@Nullable String[] types) {
        if (types == null) return true;
        for (String t : types) {
            if (t == null) continue;
            for (String part : t.split(",")) {
                String p = part.trim().toLowerCase(java.util.Locale.ROOT);
                if (p.isEmpty()) continue;
                boolean image = p.startsWith("image/") || p.equals(".jpg") || p.equals(".jpeg") || p.equals(".png")
                        || p.equals(".heic") || p.equals(".heif") || p.equals(".webp") || p.equals(".gif");
                if (!image) return false;
            }
        }
        return true;
    }

    private void deliverFiles(@Nullable Uri[] uris) {
        if (pendingFiles == null) return;
        pendingFiles.onReceiveValue(uris == null || uris.length == 0 ? null : uris);
        pendingFiles = null;
    }

    @Nullable
    private static Uri[] toArray(@Nullable List<Uri> uris) {
        return uris == null || uris.isEmpty() ? null : uris.toArray(new Uri[0]);
    }

    // ------------------------------------------------------------------ UPI / app links
    private void openOutside(Uri u, String raw) {
        Intent i;
        String fallback = null;
        try {
            if ("intent".equalsIgnoreCase(u.getScheme())) {
                i = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
                // Only let the page open apps the normal way, never a specific internal component.
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
                fallback = i.getStringExtra("browser_fallback_url");
            } else {
                i = new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.shop_no_app, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i); // UPI app, WhatsApp, dialer, mail…
        } catch (ActivityNotFoundException e) {
            if (fallback != null && fallback.startsWith("https://")) b.web.loadUrl(fallback);
            else Toast.makeText(this, R.string.shop_no_app, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.shop_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (b != null) b.web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles = null; }
        if (b != null) {
            b.web.stopLoading();
            b.web.destroy();
        }
        super.onDestroy();
    }
}

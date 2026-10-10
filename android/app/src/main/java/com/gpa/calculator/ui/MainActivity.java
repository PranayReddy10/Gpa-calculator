package com.gpa.calculator.ui;

import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.gpa.calculator.BuildConfig;
import com.gpa.calculator.R;
import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.ads.ConsentManager;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.data.remote.PopupRules;
import com.gpa.calculator.data.remote.model.RemoteConfig;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.push.PushManager;
import com.gpa.calculator.ui.calc.CalcLauncher;
import com.gpa.calculator.ui.common.PopupDialog;
import com.gpa.calculator.ui.common.RatePrompt;
import com.gpa.calculator.ui.common.ShareUtil;
import com.gpa.calculator.databinding.ActivityMainBinding;
import com.gpa.calculator.ui.home.FavouritesFragment;
import com.gpa.calculator.ui.home.HistoryFragment;
import com.gpa.calculator.ui.home.HomeFragment;
import com.gpa.calculator.ui.home.SettingsFragment;

public class MainActivity extends BaseActivity {
    private static final int UPDATE_REQUEST = 7001;
    private ActivityMainBinding b;
    private AdsManager.BannerHandle banner;
    private int currentTab = R.id.nav_home;
    /** One pop-up (admin campaign or notification prompt) per app session at most. */
    private static boolean sessionPopupShown;
    private boolean adsReady;
    private final ConfigStore.Listener configListener = cfg -> {
        loadBanner();
        if (adsReady) maybeShowPopup();
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Container-transform exit for calculator cards
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        setExitSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementsUseOverlay(false);
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == currentTab) return true;
            currentTab = item.getItemId();
            showTab(currentTab);
            return true;
        });
        b.bottomNav.setOnItemReselectedListener(item -> { /* no-op keeps state */ });

        if (savedInstanceState == null) {
            showTab(R.id.nav_home);
            if (Prefs.get(this).openCount() <= 1) {
                Snackbar.make(b.getRoot(), R.string.first_launch_tip, Snackbar.LENGTH_LONG).setAnchorView(b.bottomNav).show();
            }
        } else {
            currentTab = savedInstanceState.getInt("tab", R.id.nav_home);
            b.bottomNav.setSelectedItemId(currentTab);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (currentTab != R.id.nav_home) {
                    b.bottomNav.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Consent first, then ads.
        ConsentManager.gather(this, canRequestAds -> {
            AdsManager.get(this).start(canRequestAds);
            loadBanner();
            adsReady = true;
            maybeShowPopup();
        });

        ConfigStore.get(this).addListener(configListener);
        handleDeepLink(getIntent());
        checkUpdates();
        Analytics.get(this).log("screen_view", "screen", "main");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", currentTab);
    }

    private void showTab(int id) {
        Fragment f;
        String tag;
        if (id == R.id.nav_favourites) { f = new FavouritesFragment(); tag = "fav"; }
        else if (id == R.id.nav_history) { f = new HistoryFragment(); tag = "history"; }
        else if (id == R.id.nav_settings) { f = new SettingsFragment(); tag = "settings"; }
        else { f = new HomeFragment(); tag = "home"; }
        f.setEnterTransition(new MaterialFadeThrough());
        f.setExitTransition(new MaterialFadeThrough());
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, f, tag)
                .commit();
    }

    private void loadBanner() {
        if (banner != null) banner.destroy();
        banner = AdsManager.get(this).attachBanner(this, b.adContainer, "home");
    }

    // ------------------------------------------------------------------ push deep links
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    /** Opens the calculator / URL named by a tapped push notification (see PushManager). */
    private void handleDeepLink(@Nullable Intent intent) {
        if (intent == null) return;
        String calcId = intent.getStringExtra(PushManager.EXTRA_OPEN_CALC);
        String url = intent.getStringExtra(PushManager.EXTRA_OPEN_URL);
        intent.removeExtra(PushManager.EXTRA_OPEN_CALC);
        intent.removeExtra(PushManager.EXTRA_OPEN_URL);
        if (calcId == null && url == null) return;
        sessionPopupShown = true; // the user came for something specific; don't interrupt with a pop-up
        Calculator c = calcId == null ? null : CalculatorRegistry.byId(calcId);
        if (c != null) {
            b.getRoot().postDelayed(() -> CalcLauncher.open(this, null, c, null, false), 350);
        } else if (url != null) {
            openUrl(url);
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ pop-ups
    /** Admin campaign pop-up if its rules match, otherwise (maybe) the notification soft prompt. */
    private void maybeShowPopup() {
        if (sessionPopupShown || isFinishing()) return;
        RemoteConfig.Popup p = ConfigStore.get(this).config().app.popup;
        Prefs prefs = Prefs.get(this);
        PopupRules.State st = new PopupRules.State();
        st.now = System.currentTimeMillis();
        st.versionCode = BuildConfig.VERSION_CODE;
        st.firstOpenAt = prefs.firstOpenAt();
        st.lastShownAt = p.id == null ? 0 : prefs.getLong("popup_shown_" + p.id, 0);
        st.shownThisSession = sessionPopupShown;
        if (PopupRules.shouldShow(p, st)) {
            sessionPopupShown = true;
            b.getRoot().postDelayed(() -> showPopupWhenFree(p, 0), Math.max(0, p.delay_sec) * 1000L);
        } else {
            b.getRoot().postDelayed(this::maybeShowPushPrompt, 4000);
        }
    }

    /** Waits until no interstitial / app-open ad or consent form is on screen. */
    private void showPopupWhenFree(RemoteConfig.Popup p, int attempt) {
        if (isFinishing() || isDestroyed()) return;
        if (AdsManager.get(this).isFullScreenShowing() || !hasWindowFocus()) {
            if (attempt < 15) b.getRoot().postDelayed(() -> showPopupWhenFree(p, attempt + 1), 2000);
            return;
        }
        Prefs.get(this).putLong("popup_shown_" + p.id, System.currentTimeMillis());
        Analytics.get(this).log("popup_shown", "id", p.id);
        PopupDialog.with(this)
                .title(p.title)
                .body(p.body)
                .image(p.image_url)
                .primary(p.primary_label == null || p.primary_label.isEmpty() ? getString(R.string.ok) : p.primary_label, () -> {
                    Analytics.get(this).log("popup_click", "id", p.id, "action", p.primary_action);
                    runPopupAction(p);
                })
                .secondary(p.secondary_label, () -> Analytics.get(this).log("popup_dismiss", "id", p.id))
                .onDismiss(() -> Analytics.get(this).log("popup_dismiss", "id", p.id))
                .show();
    }

    private void runPopupAction(RemoteConfig.Popup p) {
        String v = p.primary_value == null ? "" : p.primary_value.trim();
        switch (p.primary_action == null ? "dismiss" : p.primary_action) {
            case "url":
                if (!v.isEmpty()) openUrl(v);
                break;
            case "calculator": {
                Calculator c = CalculatorRegistry.byId(v);
                if (c != null) CalcLauncher.open(this, null, c, null, false);
                break;
            }
            case "rate":
                RatePrompt.openStore(this);
                break;
            case "share": {
                String text = ConfigStore.get(this).config().app.share_text;
                ShareUtil.shareText(this, text == null || text.isEmpty() ? getString(R.string.share_text_default) : text);
                break;
            }
            case "update":
                openUrl("https://play.google.com/store/apps/details?id=" + getPackageName());
                break;
            case "notifications":
                PushManager.requestPermission(this, granted -> {
                    if (!granted) Snackbar.make(b.getRoot(), R.string.push_denied, Snackbar.LENGTH_LONG)
                            .setAction(R.string.open_settings, x -> PushManager.openSystemSettings(this))
                            .setAnchorView(b.bottomNav).show();
                });
                break;
            default:
                break;
        }
    }

    private void maybeShowPushPrompt() {
        if (sessionPopupShown || isFinishing() || !hasWindowFocus() || AdsManager.get(this).isFullScreenShowing()) return;
        if (PushManager.maybeShowSoftPrompt(this)) sessionPopupShown = true;
    }

    private void checkUpdates() {
        RemoteConfig.App app = ConfigStore.get(this).config().app;
        boolean force = app.force_update_below > BuildConfig.VERSION_CODE;
        boolean available = app.latest_version_code > BuildConfig.VERSION_CODE;
        if (!force && (!available || !ConfigStore.get(this).config().features.in_app_update)) return;
        AppUpdateManager m = AppUpdateManagerFactory.create(this);
        m.getAppUpdateInfo().addOnSuccessListener(info -> {
            int type = force ? AppUpdateType.IMMEDIATE : AppUpdateType.FLEXIBLE;
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(type)) {
                try {
                    m.startUpdateFlowForResult(info, this, AppUpdateOptions.newBuilder(type).build(), UPDATE_REQUEST);
                } catch (IntentSender.SendIntentException ignored) {
                    showStoreUpdateDialog(app, force);
                }
            } else if (force) {
                showStoreUpdateDialog(app, true);
            }
        }).addOnFailureListener(e -> { if (force) showStoreUpdateDialog(app, true); });
    }

    private void showStoreUpdateDialog(RemoteConfig.App app, boolean force) {
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_available)
                .setMessage(app.update_message)
                .setCancelable(!force)
                .setPositiveButton(R.string.update_now, (dlg, w) -> {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    if (force) finish();
                });
        if (!force) d.setNegativeButton(R.string.later, null);
        d.show();
    }

    @Override
    protected void onDestroy() {
        ConfigStore.get(this).removeListener(configListener);
        if (banner != null) banner.destroy();
        super.onDestroy();
    }
}

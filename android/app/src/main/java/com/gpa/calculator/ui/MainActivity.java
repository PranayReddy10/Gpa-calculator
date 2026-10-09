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
import com.gpa.calculator.data.remote.model.RemoteConfig;
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
    private final ConfigStore.Listener configListener = cfg -> {
        showAnnouncement(cfg);
        loadBanner();
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
        });

        ConfigStore.get(this).addListener(configListener);
        showAnnouncement(ConfigStore.get(this).config());
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

    private void showAnnouncement(RemoteConfig cfg) {
        RemoteConfig.Announcement a = cfg.app.announcement;
        if (a == null || !a.enabled || a.id == null || a.id.isEmpty()) return;
        if (a.id.equals(Prefs.get(this).seenAnnouncementId())) return;
        Prefs.get(this).setSeenAnnouncementId(a.id);
        MaterialAlertDialogBuilder d = new MaterialAlertDialogBuilder(this)
                .setTitle(a.title)
                .setMessage(a.body)
                .setPositiveButton(a.button == null || a.button.isEmpty() ? getString(R.string.ok) : a.button, (dlg, w) -> {
                    if (a.url != null && !a.url.isEmpty()) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.url))); } catch (Exception ignored) {}
                    }
                });
        if (a.url != null && !a.url.isEmpty()) d.setNegativeButton(R.string.later, null);
        d.show();
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

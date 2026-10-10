package com.gpa.calculator.ads;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.data.remote.model.RemoteConfig;
import com.gpa.calculator.ui.SplashActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central ad orchestration. Reads the admin-panel config (network priority, frequency caps, on/off
 * switches) and walks the enabled providers in order until one serves. Nothing here is network
 * specific; see the AdProvider implementations.
 */
public final class AdsManager implements ConfigStore.Listener {
    /** Filter Logcat by this tag to follow every ad decision. */
    public static final String TAG = "GPAAds";
    private static AdsManager instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, AdProvider> providers = new LinkedHashMap<>();
    private boolean consentOk;
    private boolean started;
    private long lastInterstitialAt;
    private long lastAppOpenAt;
    private boolean fullScreenShowing;
    /** Placements requested before any network finished initialising; run as soon as one does. */
    private final List<Runnable> whenReady = new ArrayList<>();
    private int calcsSinceInterstitial;
    private int opensSinceInterstitial;
    @Nullable private Activity currentActivity;

    private AdsManager(Context c) {
        app = c.getApplicationContext();
        providers.put("admob", new AdmobProvider());
        providers.put("max", new MaxProvider());
        providers.put("meta", new MetaProvider());
        ConfigStore.get(app).addListener(this);
        trackActivities();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(@NonNull LifecycleOwner owner) {
                main.postDelayed(() -> maybeShowAppOpen(currentActivity), 150);
            }
        });
    }

    public static synchronized AdsManager get(Context c) {
        if (instance == null) instance = new AdsManager(c);
        return instance;
    }

    private RemoteConfig.Ads cfg() { return ConfigStore.get(app).config().ads; }

    // ------------------------------------------------------------------ lifecycle
    private void trackActivities() {
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) { currentActivity = a; }
            @Override public void onActivityResumed(@NonNull Activity a) { currentActivity = a; }
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) { if (currentActivity == a) currentActivity = null; }
        });
    }

    /** Call from the first visible Activity after consent has been gathered. */
    public void start(boolean consentGranted) {
        consentOk = consentGranted;
        Log.i(TAG, "start: consent=" + consentGranted + ", config fetched=" + (ConfigStore.get(app).config().fetched_at > 0));
        if (started) return;
        started = true;
        initProviders();
    }

    @Override
    public void onConfigChanged(RemoteConfig config) {
        if (started) initProviders();
    }

    private void initProviders() {
        if (!consentOk) { Log.i(TAG, "not starting ads: consent not given"); return; }
        RemoteConfig.Ads ads = cfg();
        if (!ads.enabled) { Log.i(TAG, "not starting ads: ads disabled in admin panel"); return; }
        int started = 0;
        for (String name : ads.priority) {
            AdProvider p = providers.get(name);
            RemoteConfig.Network n = ads.network(name);
            if (p == null || n == null || !n.enabled) continue;
            started++;
            Log.i(TAG, "initialising " + name + (ads.test_mode ? " (test mode)" : ""));
            p.init(app, n, ads.test_mode, () -> main.post(() -> {
                Log.i(TAG, name + " ready");
                preloadInterstitial(p);
                preloadAppOpen(p);
                runWhenReady();
            }));
        }
        if (started == 0) Log.i(TAG, "not starting ads: no network enabled (priority " + ads.priority + ")");
    }

    private void runWhenReady() {
        if (whenReady.isEmpty()) return;
        List<Runnable> pending = new ArrayList<>(whenReady);
        whenReady.clear();
        for (Runnable r : pending) r.run();
    }

    /** Runs now if a network is ready, otherwise as soon as the first one finishes initialising. */
    private void whenAnyReady(Runnable r) {
        if (!ordered().isEmpty()) main.post(r);
        else whenReady.add(r);
    }

    private String skipReason() {
        if (!started) return "ads not started yet (waiting for consent check)";
        if (!consentOk) return "no consent";
        if (!cfg().enabled) return "ads disabled in admin panel";
        if (isAdFree()) return "ad-free reward active";
        return "";
    }

    /** Human-readable state for the diagnostics screen. */
    public String diagnostics() {
        RemoteConfig.Ads a = cfg();
        StringBuilder sb = new StringBuilder();
        sb.append("Ads enabled: ").append(a.enabled).append(a.test_mode ? " (test mode)" : "").append('\n');
        sb.append("Started: ").append(started).append(", consent ok: ").append(consentOk).append('\n');
        if (ConsentManager.lastError != null) sb.append("Consent error: ").append(ConsentManager.lastError).append('\n');
        if (isAdFree()) sb.append("Ad-free for ").append(adFreeRemainingMs() / 60000).append(" min\n");
        sb.append("Priority: ").append(a.priority).append('\n');
        for (String name : new String[]{"admob", "max", "meta"}) {
            RemoteConfig.Network n = a.network(name);
            AdProvider p = providers.get(name);
            sb.append("• ").append(name).append(": ").append(n != null && n.enabled ? "on" : "off")
                    .append(p != null && p.isInitialized() ? ", ready" : ", not initialised");
            if (p != null) sb.append(", interstitial ").append(p.isInterstitialReady() ? "loaded" : "not loaded");
            sb.append('\n');
        }
        sb.append("Formats: banner ").append(a.banner_enabled).append(", native ").append(a.native_enabled)
                .append(", interstitial ").append(a.interstitial_enabled).append(", app open ").append(a.app_open_enabled);
        return sb.toString();
    }

    /** True while an interstitial, app-open or rewarded ad is on screen. */
    public boolean isFullScreenShowing() { return fullScreenShowing; }

    // ------------------------------------------------------------------ gating
    public boolean isAdFree() {
        return Prefs.get(app).adFreeUntil() > System.currentTimeMillis();
    }

    public long adFreeRemainingMs() {
        return Math.max(0, Prefs.get(app).adFreeUntil() - System.currentTimeMillis());
    }

    private boolean adsAllowed() {
        return started && consentOk && cfg().enabled && !isAdFree();
    }

    private List<AdProvider> ordered() {
        List<AdProvider> out = new ArrayList<>();
        RemoteConfig.Ads ads = cfg();
        for (String name : ads.priority) {
            AdProvider p = providers.get(name);
            RemoteConfig.Network n = ads.network(name);
            if (p != null && n != null && n.enabled && p.isInitialized()) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ banner
    public static final class BannerHandle {
        AdProvider provider;
        View view;
        boolean cancelled;

        public void destroy() {
            cancelled = true;
            if (provider != null && view != null) {
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                provider.destroyBanner(view);
            }
            view = null;
        }
    }

    /** Loads a banner into the container, trying each network in priority order. */
    public BannerHandle attachBanner(Activity activity, ViewGroup container, String placement) {
        BannerHandle handle = new BannerHandle();
        RemoteConfig.Ads ads = cfg();
        boolean placementOn = "home".equals(placement) ? ads.banner_on_home
                : "result".equals(placement) ? ads.banner_on_result : ads.banner_on_calculator;
        if (!adsAllowed() || !ads.banner_enabled || !placementOn) {
            Log.i(TAG, "banner " + placement + " skipped: " + skipReason() + (ads.banner_enabled ? "" : " banners off") + (placementOn ? "" : " placement off"));
            container.setVisibility(View.GONE);
            return handle;
        }
        whenAnyReady(() -> {
            if (!handle.cancelled && !activity.isFinishing() && !activity.isDestroyed())
                tryBanner(activity, container, ordered(), 0, handle, placement);
        });
        return handle;
    }

    private void tryBanner(Activity activity, ViewGroup container, List<AdProvider> list, int idx, BannerHandle handle, String placement) {
        if (handle.cancelled || activity.isFinishing() || activity.isDestroyed()) return;
        if (idx >= list.size()) {
            Log.i(TAG, "banner " + placement + ": no network filled");
            container.setVisibility(View.GONE);
            return;
        }
        AdProvider p = list.get(idx);
        p.loadBanner(activity, container, new AdProvider.BannerCallback() {
            @Override public void onLoaded(View banner) {
                if (handle.cancelled) { p.destroyBanner(banner); return; }
                container.removeAllViews();
                container.addView(banner);
                container.setVisibility(View.VISIBLE);
                container.setAlpha(0f);
                container.animate().alpha(1f).setDuration(350).start();
                handle.provider = p;
                handle.view = banner;
                Analytics.get(app).log("ad_impression", "network", p.name(), "format", "banner_" + placement);
            }

            @Override public void onFailed(String reason) {
                Log.i(TAG, p.name() + " banner failed: " + reason);
                tryBanner(activity, container, list, idx + 1, handle, placement);
            }
        });
    }

    // ------------------------------------------------------------------ interstitial
    private void preloadInterstitial(AdProvider p) {
        if (!cfg().interstitial_enabled) return;
        p.loadInterstitial(app, new AdProvider.LoadCallback() {
            @Override public void onLoaded() { Log.i(TAG, p.name() + " interstitial ready"); }
            @Override public void onFailed(String reason) { Log.i(TAG, p.name() + " interstitial failed: " + reason); }
        });
    }

    /** Call after every completed calculation (frequency from the admin panel). */
    public void onCalculation(Activity activity, @Nullable Runnable after) {
        calcsSinceInterstitial++;
        RemoteConfig.Ads ads = cfg();
        boolean due = ads.interstitial_after_calc && calcsSinceInterstitial >= Math.max(1, ads.interstitial_every_n_calcs);
        showInterstitialIfDue(due, activity, "after_calc", after);
    }

    /** Call when the user taps a calculator; {@code after} opens the screen (runs after the ad, or immediately). */
    public void onCalculatorOpen(Activity activity, Runnable after) {
        opensSinceInterstitial++;
        RemoteConfig.Ads ads = cfg();
        boolean due = ads.interstitial_on_open && opensSinceInterstitial >= Math.max(1, ads.interstitial_open_every_n);
        showInterstitialIfDue(due, activity, "calc_open", after);
    }

    /** Call when the user leaves a calculator; {@code after} finishes the screen. */
    public void onCalculatorExit(Activity activity, Runnable after) {
        showInterstitialIfDue(cfg().interstitial_on_exit, activity, "calc_exit", after);
    }

    /**
     * Shared gate for every interstitial trigger: master switch, ad-free reward, min interval,
     * first-install grace period and "nothing else full-screen right now". Runs {@code after} exactly once.
     */
    private void showInterstitialIfDue(boolean due, Activity activity, String trigger, @Nullable Runnable after) {
        RemoteConfig.Ads ads = cfg();
        long now = System.currentTimeMillis();
        boolean ok = due && adsAllowed() && ads.interstitial_enabled
                && now - lastInterstitialAt >= ads.interstitial_min_interval_sec * 1000L
                && now - Prefs.get(app).firstOpenAt() >= ads.first_interstitial_delay_sec * 1000L
                && !fullScreenShowing && !activity.isFinishing();
        if (!ok) {
            if (after != null) after.run();
            return;
        }
        for (AdProvider p : ordered()) {
            if (p.isInterstitialReady()) {
                fullScreenShowing = true;
                p.showInterstitial(activity, new AdProvider.ShowCallback() {
                    @Override public void onDismissed() {
                        fullScreenShowing = false;
                        lastInterstitialAt = System.currentTimeMillis();
                        calcsSinceInterstitial = 0;
                        opensSinceInterstitial = 0;
                        Analytics.get(app).log("ad_impression", "network", p.name(), "format", "interstitial_" + trigger);
                        preloadInterstitial(p);
                        if (after != null) after.run();
                    }

                    @Override public void onFailed(String reason) {
                        fullScreenShowing = false;
                        preloadInterstitial(p);
                        if (after != null) after.run();
                    }
                });
                return;
            }
            preloadInterstitial(p);
        }
        if (after != null) after.run();
    }

    // ------------------------------------------------------------------ native
    public static final class NativeHandle {
        AdProvider provider;
        View view;
        boolean cancelled;

        public void destroy() {
            cancelled = true;
            if (provider != null && view != null) {
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                provider.destroyNative(view);
            }
            view = null;
        }
    }

    /**
     * Loads a native ad (our own card layout) into {@code container}, walking the networks in the
     * admin-panel priority order. The container stays hidden unless an ad loads.
     */
    public NativeHandle attachNative(Activity activity, ViewGroup container, String placement, boolean compact) {
        NativeHandle handle = new NativeHandle();
        container.setVisibility(View.GONE);
        RemoteConfig.Ads ads = cfg();
        if (!adsAllowed() || !ads.native_enabled || !ads.native_in_calculators) {
            Log.i(TAG, "native " + placement + " skipped: " + skipReason() + (ads.native_enabled && ads.native_in_calculators ? "" : " native off"));
            return handle;
        }
        whenAnyReady(() -> {
            if (!handle.cancelled && !activity.isFinishing() && !activity.isDestroyed())
                tryNative(activity, container, ordered(), 0, handle, placement, compact);
        });
        return handle;
    }

    private void tryNative(Activity activity, ViewGroup container, List<AdProvider> list, int idx, NativeHandle handle, String placement, boolean compact) {
        if (handle.cancelled || activity.isFinishing() || activity.isDestroyed()) return;
        if (idx >= list.size()) { Log.i(TAG, "native " + placement + ": no network filled"); return; }
        AdProvider p = list.get(idx);
        p.loadNative(activity, container, compact, new AdProvider.NativeCallback() {
            @Override public void onLoaded(View adView) {
                if (handle.cancelled || activity.isDestroyed()) { p.destroyNative(adView); return; }
                container.removeAllViews();
                container.addView(adView);
                container.setVisibility(View.VISIBLE);
                container.setAlpha(0f);
                container.setTranslationY(24 * activity.getResources().getDisplayMetrics().density);
                container.animate().alpha(1f).translationY(0f).setDuration(380)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                handle.provider = p;
                handle.view = adView;
                Analytics.get(app).log("ad_impression", "network", p.name(), "format", (compact ? "native_small_" : "native_") + placement);
            }

            @Override public void onFailed(String reason) {
                Log.i(TAG, p.name() + " native failed: " + reason);
                tryNative(activity, container, list, idx + 1, handle, placement, compact);
            }
        });
    }

    // ------------------------------------------------------------------ app open
    private void preloadAppOpen(AdProvider p) {
        if (!cfg().app_open_enabled || !p.supportsAppOpen()) return;
        p.loadAppOpen(app, new AdProvider.LoadCallback() {
            @Override public void onLoaded() { Log.i(TAG, p.name() + " app open ready"); }
            @Override public void onFailed(String reason) { Log.i(TAG, p.name() + " app open failed: " + reason); }
        });
    }

    private void maybeShowAppOpen(@Nullable Activity activity) {
        if (activity == null || activity instanceof SplashActivity || activity.isFinishing()) return;
        RemoteConfig.Ads ads = cfg();
        long now = System.currentTimeMillis();
        if (!adsAllowed() || !ads.app_open_enabled || fullScreenShowing) return;
        if (now - lastAppOpenAt < ads.app_open_min_interval_sec * 1000L) return;
        if (now - Prefs.get(app).firstOpenAt() < 5000) return; // never on the very first launch
        for (AdProvider p : ordered()) {
            if (p.isAppOpenReady()) {
                fullScreenShowing = true;
                p.showAppOpen(activity, new AdProvider.ShowCallback() {
                    @Override public void onDismissed() {
                        fullScreenShowing = false;
                        lastAppOpenAt = System.currentTimeMillis();
                        Analytics.get(app).log("ad_impression", "network", p.name(), "format", "app_open");
                        preloadAppOpen(p);
                    }

                    @Override public void onFailed(String reason) {
                        fullScreenShowing = false;
                        preloadAppOpen(p);
                    }
                });
                return;
            }
            preloadAppOpen(p);
        }
    }

    // ------------------------------------------------------------------ rewarded (ad-free time)
    public boolean rewardedAvailable() {
        return started && consentOk && cfg().enabled && cfg().rewarded_enabled && !ordered().isEmpty();
    }

    public void preloadRewarded() {
        if (!rewardedAvailable()) return;
        for (AdProvider p : ordered()) {
            p.loadRewarded(app, new AdProvider.LoadCallback() {
                @Override public void onLoaded() {}
                @Override public void onFailed(String reason) {}
            });
        }
    }

    public interface RewardResult {
        void onResult(boolean rewarded, String message);
    }

    /** Shows a rewarded ad; on completion grants the ad-free window configured in the admin panel. */
    public void showRewarded(Activity activity, RewardResult result) {
        if (!rewardedAvailable()) {
            result.onResult(false, "Not available right now");
            return;
        }
        for (AdProvider p : ordered()) {
            if (p.isRewardedReady()) {
                fullScreenShowing = true;
                final boolean[] earned = {false};
                p.showRewarded(activity, new AdProvider.RewardCallback() {
                    @Override public void onRewarded() {
                        earned[0] = true;
                        long until = System.currentTimeMillis() + cfg().reward_ad_free_minutes * 60_000L;
                        Prefs.get(app).setAdFreeUntil(until);
                        Analytics.get(app).log("reward_earned", "network", p.name());
                    }

                    @Override public void onDismissed() {
                        fullScreenShowing = false;
                        result.onResult(earned[0], earned[0] ? "Enjoy " + cfg().reward_ad_free_minutes + " minutes without ads" : "Watch the full video to remove ads");
                    }

                    @Override public void onFailed(String reason) {
                        fullScreenShowing = false;
                        result.onResult(false, reason);
                    }
                });
                return;
            }
        }
        preloadRewarded();
        result.onResult(false, "Ad is still loading, try again in a moment");
    }
}

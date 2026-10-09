package com.gpa.calculator.ads;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.gpa.calculator.data.remote.model.RemoteConfig;

/** Google AdMob. Uses Google's public test unit IDs whenever test mode is on in the admin panel. */
public final class AdmobProvider implements AdProvider {
    private static final String TAG = "AdmobProvider";
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    private RemoteConfig.Network cfg = new RemoteConfig.Network();
    private boolean testMode;
    private boolean initialized;
    private InterstitialAd interstitial;
    private AppOpenAd appOpen;
    private RewardedAd rewarded;
    private boolean loadingInterstitial, loadingAppOpen, loadingRewarded;

    @Override public String name() { return "admob"; }

    @Override
    public void init(Context context, RemoteConfig.Network cfg, boolean testMode, Runnable onReady) {
        this.cfg = cfg;
        this.testMode = testMode;
        if (initialized) {
            onReady.run();
            return;
        }
        MobileAds.initialize(context.getApplicationContext(), status -> {
            initialized = true;
            Log.i(TAG, "initialized");
            onReady.run();
        });
    }

    @Override public boolean isInitialized() { return initialized; }

    private String id(String real, String test) {
        return testMode || real == null || real.isEmpty() ? test : real;
    }

    private static AdRequest request() { return new AdRequest.Builder().build(); }

    // ---------------------------------------------------------------- banner
    @Override
    public void loadBanner(Activity activity, ViewGroup container, BannerCallback cb) {
        AdView view = new AdView(activity);
        view.setAdUnitId(id(cfg.banner, TEST_BANNER));
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int widthPx = container.getWidth() > 0 ? container.getWidth() : dm.widthPixels;
        int widthDp = (int) (widthPx / dm.density);
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
        view.setAdListener(new AdListener() {
            @Override public void onAdLoaded() { cb.onLoaded(view); }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                view.destroy();
                cb.onFailed(e.getMessage());
            }
        });
        view.loadAd(request());
    }

    @Override
    public void destroyBanner(View banner) {
        if (banner instanceof AdView) ((AdView) banner).destroy();
    }

    // ---------------------------------------------------------------- interstitial
    @Override
    public void loadInterstitial(Context context, LoadCallback cb) {
        if (interstitial != null || loadingInterstitial) return;
        loadingInterstitial = true;
        InterstitialAd.load(context, id(cfg.interstitial, TEST_INTERSTITIAL), request(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                loadingInterstitial = false;
                interstitial = ad;
                cb.onLoaded();
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                loadingInterstitial = false;
                interstitial = null;
                cb.onFailed(e.getMessage());
            }
        });
    }

    @Override public boolean isInterstitialReady() { return interstitial != null; }

    @Override
    public void showInterstitial(Activity activity, ShowCallback cb) {
        InterstitialAd ad = interstitial;
        interstitial = null;
        if (ad == null) {
            cb.onFailed("not loaded");
            return;
        }
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { cb.onDismissed(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { cb.onFailed(e.getMessage()); }
        });
        ad.show(activity);
    }

    // ---------------------------------------------------------------- app open
    @Override public boolean supportsAppOpen() { return true; }

    @Override
    public void loadAppOpen(Context context, LoadCallback cb) {
        if (appOpen != null || loadingAppOpen) return;
        loadingAppOpen = true;
        AppOpenAd.load(context, id(cfg.app_open, TEST_APP_OPEN), request(), new AppOpenAd.AppOpenAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull AppOpenAd ad) {
                loadingAppOpen = false;
                appOpen = ad;
                cb.onLoaded();
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                loadingAppOpen = false;
                appOpen = null;
                cb.onFailed(e.getMessage());
            }
        });
    }

    @Override public boolean isAppOpenReady() { return appOpen != null; }

    @Override
    public void showAppOpen(Activity activity, ShowCallback cb) {
        AppOpenAd ad = appOpen;
        appOpen = null;
        if (ad == null) {
            cb.onFailed("not loaded");
            return;
        }
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { cb.onDismissed(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { cb.onFailed(e.getMessage()); }
        });
        ad.show(activity);
    }

    // ---------------------------------------------------------------- rewarded
    @Override
    public void loadRewarded(Context context, LoadCallback cb) {
        if (rewarded != null || loadingRewarded) return;
        loadingRewarded = true;
        RewardedAd.load(context, id(cfg.rewarded, TEST_REWARDED), request(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                loadingRewarded = false;
                rewarded = ad;
                cb.onLoaded();
            }

            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) {
                loadingRewarded = false;
                rewarded = null;
                cb.onFailed(e.getMessage());
            }
        });
    }

    @Override public boolean isRewardedReady() { return rewarded != null; }

    @Override
    public void showRewarded(Activity activity, RewardCallback cb) {
        RewardedAd ad = rewarded;
        rewarded = null;
        if (ad == null) {
            cb.onFailed("not loaded");
            return;
        }
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { cb.onDismissed(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { cb.onFailed(e.getMessage()); }
        });
        ad.show(activity, item -> cb.onRewarded());
    }
}

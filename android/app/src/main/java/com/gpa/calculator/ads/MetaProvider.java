package com.gpa.calculator.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.AdListener;
import com.facebook.ads.AdSettings;
import com.facebook.ads.AdSize;
import com.facebook.ads.AudienceNetworkAds;
import com.facebook.ads.InterstitialAd;
import com.facebook.ads.InterstitialAdListener;
import com.facebook.ads.RewardedVideoAd;
import com.facebook.ads.RewardedVideoAdListener;
import com.gpa.calculator.data.remote.model.RemoteConfig;

/** Meta (Facebook) Audience Network. No app-open format, so it is skipped for that slot. */
public final class MetaProvider implements AdProvider {
    private static final String TAG = "MetaProvider";

    private RemoteConfig.Network cfg = new RemoteConfig.Network();
    private boolean initialized;
    private InterstitialAd interstitial;
    private RewardedVideoAd rewarded;
    private boolean loadingInterstitial, loadingRewarded;

    @Override public String name() { return "meta"; }

    @Override
    public void init(Context context, RemoteConfig.Network cfg, boolean testMode, Runnable onReady) {
        this.cfg = cfg;
        if (initialized) {
            onReady.run();
            return;
        }
        if (testMode) AdSettings.setTestMode(true);
        AudienceNetworkAds.buildInitSettings(context.getApplicationContext())
                .withInitListener(result -> {
                    initialized = result.isSuccess();
                    Log.i(TAG, "init: " + result.getMessage());
                    if (initialized) onReady.run();
                })
                .initialize();
    }

    @Override public boolean isInitialized() { return initialized; }

    private boolean has(String id) { return id != null && !id.isEmpty(); }

    // ---------------------------------------------------------------- banner
    @Override
    public void loadBanner(Activity activity, ViewGroup container, BannerCallback cb) {
        if (!has(cfg.banner)) { cb.onFailed("no banner placement"); return; }
        com.facebook.ads.AdView view = new com.facebook.ads.AdView(activity, cfg.banner, AdSize.BANNER_HEIGHT_50);
        view.loadAd(view.buildLoadAdConfig().withAdListener(new AdListener() {
            @Override public void onError(Ad ad, AdError e) { view.destroy(); cb.onFailed(e.getErrorMessage()); }
            @Override public void onAdLoaded(Ad ad) { cb.onLoaded(view); }
            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        }).build());
    }

    @Override
    public void destroyBanner(View banner) {
        if (banner instanceof com.facebook.ads.AdView) ((com.facebook.ads.AdView) banner).destroy();
    }

    // ---------------------------------------------------------------- interstitial
    private ShowCallback showCb;

    @Override
    public void loadInterstitial(Context context, LoadCallback cb) {
        if (!has(cfg.interstitial)) { cb.onFailed("no interstitial placement"); return; }
        if (loadingInterstitial || isInterstitialReady()) return;
        loadingInterstitial = true;
        if (interstitial != null) interstitial.destroy();
        interstitial = new InterstitialAd(context.getApplicationContext(), cfg.interstitial);
        interstitial.loadAd(interstitial.buildLoadAdConfig().withAdListener(new InterstitialAdListener() {
            @Override public void onInterstitialDisplayed(Ad ad) {}
            @Override public void onInterstitialDismissed(Ad ad) { if (showCb != null) { ShowCallback c = showCb; showCb = null; c.onDismissed(); } }
            @Override public void onError(Ad ad, AdError e) {
                loadingInterstitial = false;
                if (showCb != null) { ShowCallback c = showCb; showCb = null; c.onFailed(e.getErrorMessage()); }
                else cb.onFailed(e.getErrorMessage());
            }
            @Override public void onAdLoaded(Ad ad) { loadingInterstitial = false; cb.onLoaded(); }
            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        }).build());
    }

    @Override public boolean isInterstitialReady() {
        return interstitial != null && interstitial.isAdLoaded() && !interstitial.isAdInvalidated();
    }

    @Override
    public void showInterstitial(Activity activity, ShowCallback cb) {
        if (!isInterstitialReady()) { cb.onFailed("not loaded"); return; }
        showCb = cb;
        interstitial.show();
    }

    // ---------------------------------------------------------------- app open (unsupported)
    @Override public boolean supportsAppOpen() { return false; }
    @Override public void loadAppOpen(Context context, LoadCallback cb) { cb.onFailed("unsupported"); }
    @Override public boolean isAppOpenReady() { return false; }
    @Override public void showAppOpen(Activity activity, ShowCallback cb) { cb.onFailed("unsupported"); }

    // ---------------------------------------------------------------- rewarded
    private RewardCallback rewardCb;

    @Override
    public void loadRewarded(Context context, LoadCallback cb) {
        if (!has(cfg.rewarded)) { cb.onFailed("no rewarded placement"); return; }
        if (loadingRewarded || isRewardedReady()) return;
        loadingRewarded = true;
        if (rewarded != null) rewarded.destroy();
        rewarded = new RewardedVideoAd(context.getApplicationContext(), cfg.rewarded);
        rewarded.loadAd(rewarded.buildLoadAdConfig().withAdListener(new RewardedVideoAdListener() {
            @Override public void onRewardedVideoCompleted() { if (rewardCb != null) rewardCb.onRewarded(); }
            @Override public void onRewardedVideoClosed() { if (rewardCb != null) { RewardCallback c = rewardCb; rewardCb = null; c.onDismissed(); } }
            @Override public void onError(Ad ad, AdError e) {
                loadingRewarded = false;
                if (rewardCb != null) { RewardCallback c = rewardCb; rewardCb = null; c.onFailed(e.getErrorMessage()); }
                else cb.onFailed(e.getErrorMessage());
            }
            @Override public void onAdLoaded(Ad ad) { loadingRewarded = false; cb.onLoaded(); }
            @Override public void onAdClicked(Ad ad) {}
            @Override public void onLoggingImpression(Ad ad) {}
        }).build());
    }

    @Override public boolean isRewardedReady() {
        return rewarded != null && rewarded.isAdLoaded() && !rewarded.isAdInvalidated();
    }

    @Override
    public void showRewarded(Activity activity, RewardCallback cb) {
        if (!isRewardedReady()) { cb.onFailed("not loaded"); return; }
        rewardCb = cb;
        rewarded.show();
    }
}

package com.gpa.calculator.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxAdView;
import com.applovin.mediation.ads.MaxAppOpenAd;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.model.RemoteConfig;

/** AppLovin MAX. The SDK key and ad unit IDs come from the admin panel config. */
public final class MaxProvider implements AdProvider {
    private static final String TAG = "MaxProvider";

    private RemoteConfig.Network cfg = new RemoteConfig.Network();
    private boolean initialized;
    private MaxInterstitialAd interstitial;
    private MaxAppOpenAd appOpen;
    private MaxRewardedAd rewarded;
    private boolean interstitialReady, appOpenReady, rewardedReady;

    @Override public String name() { return "max"; }

    /** SDK key is read from the manifest meta-data "applovin.sdk.key" (set via the APPLOVIN_SDK_KEY gradle property). */
    private static String manifestSdkKey(Context context) {
        try {
            android.content.pm.ApplicationInfo ai = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            Object v = ai.metaData == null ? null : ai.metaData.get("applovin.sdk.key");
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void init(Context context, RemoteConfig.Network cfg, boolean testMode, Runnable onReady) {
        this.cfg = cfg;
        if (initialized) {
            onReady.run();
            return;
        }
        if (manifestSdkKey(context).isEmpty()) {
            Log.w(TAG, "applovin.sdk.key missing from manifest; MAX disabled");
            return;
        }
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration
                .builder(context.getApplicationContext())
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .build();
        AppLovinSdk sdk = AppLovinSdk.getInstance(context.getApplicationContext());
        sdk.getSettings().setVerboseLogging(testMode);
        sdk.initialize(initConfig, sdkConfig -> {
            initialized = true;
            Log.i(TAG, "initialized");
            onReady.run();
        });
    }

    @Override public boolean isInitialized() { return initialized; }

    private boolean has(String id) { return id != null && !id.isEmpty(); }

    // ---------------------------------------------------------------- banner
    @Override
    public void loadBanner(Activity activity, ViewGroup container, BannerCallback cb) {
        if (!has(cfg.banner)) {
            cb.onFailed("no banner unit");
            return;
        }
        MaxAdView view = new MaxAdView(cfg.banner, activity);
        int heightPx = MaxAdFormat.BANNER.getAdaptiveSize(activity).getHeight();
        heightPx = (int) (heightPx * activity.getResources().getDisplayMetrics().density);
        view.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        view.setExtraParameter("adaptive_banner", "true");
        view.setListener(new MaxAdViewAdListener() {
            boolean reported;
            @Override public void onAdLoaded(@NonNull MaxAd ad) { if (!reported) { reported = true; cb.onLoaded(view); } }
            @Override public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) {
                if (!reported) { reported = true; view.destroy(); cb.onFailed(error.getMessage()); }
            }
            @Override public void onAdDisplayed(@NonNull MaxAd ad) {}
            @Override public void onAdHidden(@NonNull MaxAd ad) {}
            @Override public void onAdClicked(@NonNull MaxAd ad) {}
            @Override public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) {}
            @Override public void onAdExpanded(@NonNull MaxAd ad) {}
            @Override public void onAdCollapsed(@NonNull MaxAd ad) {}
        });
        view.loadAd();
    }

    @Override
    public void destroyBanner(View banner) {
        if (banner instanceof MaxAdView) ((MaxAdView) banner).destroy();
    }

    // ---------------------------------------------------------------- interstitial
    @Override
    public void loadInterstitial(Context context, LoadCallback cb) {
        if (!has(cfg.interstitial)) { cb.onFailed("no interstitial unit"); return; }
        if (interstitial == null) interstitial = new MaxInterstitialAd(cfg.interstitial, context.getApplicationContext());
        if (interstitialReady) { cb.onLoaded(); return; }
        interstitial.setListener(new MaxAdListener() {
            @Override public void onAdLoaded(@NonNull MaxAd ad) { interstitialReady = true; cb.onLoaded(); }
            @Override public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) { interstitialReady = false; cb.onFailed(error.getMessage()); }
            @Override public void onAdDisplayed(@NonNull MaxAd ad) {}
            @Override public void onAdHidden(@NonNull MaxAd ad) { interstitialReady = false; if (showCb != null) { ShowCallback c = showCb; showCb = null; c.onDismissed(); } }
            @Override public void onAdClicked(@NonNull MaxAd ad) {}
            @Override public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) { interstitialReady = false; if (showCb != null) { ShowCallback c = showCb; showCb = null; c.onFailed(error.getMessage()); } }
        });
        interstitial.loadAd();
    }

    private ShowCallback showCb;

    @Override public boolean isInterstitialReady() { return interstitial != null && interstitialReady && interstitial.isReady(); }

    @Override
    public void showInterstitial(Activity activity, ShowCallback cb) {
        if (!isInterstitialReady()) { cb.onFailed("not loaded"); return; }
        showCb = cb;
        interstitial.showAd();
    }

    // ---------------------------------------------------------------- app open
    @Override public boolean supportsAppOpen() { return true; }

    private ShowCallback appOpenCb;

    @Override
    public void loadAppOpen(Context context, LoadCallback cb) {
        if (!has(cfg.app_open)) { cb.onFailed("no app open unit"); return; }
        if (appOpen == null) appOpen = new MaxAppOpenAd(cfg.app_open, context.getApplicationContext());
        if (appOpenReady) { cb.onLoaded(); return; }
        appOpen.setListener(new MaxAdListener() {
            @Override public void onAdLoaded(@NonNull MaxAd ad) { appOpenReady = true; cb.onLoaded(); }
            @Override public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) { appOpenReady = false; cb.onFailed(error.getMessage()); }
            @Override public void onAdDisplayed(@NonNull MaxAd ad) {}
            @Override public void onAdHidden(@NonNull MaxAd ad) { appOpenReady = false; if (appOpenCb != null) { ShowCallback c = appOpenCb; appOpenCb = null; c.onDismissed(); } }
            @Override public void onAdClicked(@NonNull MaxAd ad) {}
            @Override public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) { appOpenReady = false; if (appOpenCb != null) { ShowCallback c = appOpenCb; appOpenCb = null; c.onFailed(error.getMessage()); } }
        });
        appOpen.loadAd();
    }

    @Override public boolean isAppOpenReady() { return appOpen != null && appOpenReady && appOpen.isReady(); }

    @Override
    public void showAppOpen(Activity activity, ShowCallback cb) {
        if (!isAppOpenReady()) { cb.onFailed("not loaded"); return; }
        appOpenCb = cb;
        appOpen.showAd();
    }

    // ---------------------------------------------------------------- rewarded
    private RewardCallback rewardCb;
    private boolean earned;

    @Override
    public void loadRewarded(Context context, LoadCallback cb) {
        if (!has(cfg.rewarded)) { cb.onFailed("no rewarded unit"); return; }
        if (rewarded == null) rewarded = MaxRewardedAd.getInstance(cfg.rewarded, context.getApplicationContext());
        if (rewardedReady) { cb.onLoaded(); return; }
        rewarded.setListener(new MaxRewardedAdListener() {
            @Override public void onUserRewarded(@NonNull MaxAd ad, @NonNull MaxReward reward) { earned = true; if (rewardCb != null) rewardCb.onRewarded(); }
            @Override public void onAdLoaded(@NonNull MaxAd ad) { rewardedReady = true; cb.onLoaded(); }
            @Override public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) { rewardedReady = false; cb.onFailed(error.getMessage()); }
            @Override public void onAdDisplayed(@NonNull MaxAd ad) {}
            @Override public void onAdHidden(@NonNull MaxAd ad) { rewardedReady = false; if (rewardCb != null) { RewardCallback c = rewardCb; rewardCb = null; c.onDismissed(); } }
            @Override public void onAdClicked(@NonNull MaxAd ad) {}
            @Override public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) { rewardedReady = false; if (rewardCb != null) { RewardCallback c = rewardCb; rewardCb = null; c.onFailed(error.getMessage()); } }
        });
        rewarded.loadAd();
    }

    @Override public boolean isRewardedReady() { return rewarded != null && rewardedReady && rewarded.isReady(); }

    @Override
    public void showRewarded(Activity activity, RewardCallback cb) {
        if (!isRewardedReady()) { cb.onFailed("not loaded"); return; }
        earned = false;
        rewardCb = cb;
        rewarded.showAd();
    }

    // ---------------------------------------------------------------- native
    @Override
    public void loadNative(Activity activity, ViewGroup container, boolean compact, NativeCallback cb) {
        if (!has(cfg.native_ad)) { cb.onFailed("no native unit"); return; }
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(R.layout.native_ad_max)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setAdvertiserTextViewId(R.id.ad_advertiser)
                .setIconImageViewId(R.id.ad_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setOptionsContentViewGroupId(R.id.ad_options)
                .setCallToActionButtonId(R.id.ad_cta)
                .build();
        MaxNativeAdView adView = new MaxNativeAdView(binder, activity);
        if (compact) {
            adView.findViewById(R.id.ad_media).setVisibility(View.GONE);
            adView.findViewById(R.id.ad_body).setVisibility(View.GONE);
        }
        MaxNativeAdLoader loader = new MaxNativeAdLoader(cfg.native_ad, activity);
        loader.setNativeAdListener(new MaxNativeAdListener() {
            private boolean reported;

            @Override public void onNativeAdLoaded(@Nullable MaxNativeAdView view, @NonNull MaxAd ad) {
                if (reported) return;
                reported = true;
                if (activity.isFinishing() || activity.isDestroyed()) {
                    loader.destroy(ad);
                    loader.destroy();
                    return;
                }
                View out = view != null ? view : adView;
                out.setTag(R.id.native_ad_tag, new Object[]{loader, ad});
                cb.onLoaded(out);
            }

            @Override public void onNativeAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) {
                if (reported) return;
                reported = true;
                loader.destroy();
                cb.onFailed(error.getMessage());
            }

            @Override public void onNativeAdClicked(@NonNull MaxAd ad) {}
        });
        loader.loadAd(adView);
    }

    @Override
    public void destroyNative(View adView) {
        Object tag = adView.getTag(R.id.native_ad_tag);
        if (tag instanceof Object[]) {
            Object[] t = (Object[]) tag;
            MaxNativeAdLoader loader = (MaxNativeAdLoader) t[0];
            loader.destroy((MaxAd) t[1]);
            loader.destroy();
        }
    }
}

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
import com.facebook.ads.AdOptionsView;
import com.facebook.ads.AdSize;
import com.facebook.ads.MediaView;
import com.facebook.ads.NativeAd;
import com.facebook.ads.NativeAdLayout;
import com.facebook.ads.NativeAdListener;
import com.facebook.ads.AudienceNetworkAds;
import com.facebook.ads.InterstitialAd;
import com.facebook.ads.InterstitialAdListener;
import com.facebook.ads.RewardedVideoAd;
import com.facebook.ads.RewardedVideoAdListener;
import com.gpa.calculator.R;
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

    // ---------------------------------------------------------------- native
    /** Meta requires its media view on native ads, so the compact slot falls through to the next network. */
    @Override
    public void loadNative(Activity activity, ViewGroup container, boolean compact, NativeCallback cb) {
        if (!has(cfg.native_ad)) { cb.onFailed("no native placement"); return; }
        if (compact) { cb.onFailed("compact native not supported"); return; }
        NativeAd ad = new NativeAd(activity, cfg.native_ad);
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(new NativeAdListener() {
            @Override public void onMediaDownloaded(Ad a) {}

            @Override public void onError(Ad a, AdError e) {
                ad.destroy();
                cb.onFailed(e.getErrorMessage());
            }

            @Override public void onAdLoaded(Ad a) {
                if (activity.isFinishing() || activity.isDestroyed() || ad != a) {
                    ad.destroy();
                    return;
                }
                NativeAdLayout layout = new NativeAdLayout(activity);
                View content = android.view.LayoutInflater.from(activity).inflate(R.layout.native_ad_meta, layout, false);
                layout.addView(content);
                ad.unregisterView();

                android.widget.TextView headline = content.findViewById(R.id.ad_headline);
                android.widget.TextView advertiser = content.findViewById(R.id.ad_advertiser);
                android.widget.TextView sponsored = content.findViewById(R.id.ad_sponsored);
                android.widget.TextView body = content.findViewById(R.id.ad_body);
                android.widget.Button cta = content.findViewById(R.id.ad_cta);
                MediaView icon = content.findViewById(R.id.ad_icon);
                MediaView media = content.findViewById(R.id.ad_media);
                android.widget.LinearLayout options = content.findViewById(R.id.ad_options);

                headline.setText(ad.getAdvertiserName());
                advertiser.setText(ad.getAdSocialContext());
                sponsored.setText(ad.getSponsoredTranslation());
                body.setText(ad.getAdBodyText());
                cta.setVisibility(ad.hasCallToAction() ? View.VISIBLE : View.INVISIBLE);
                cta.setText(ad.getAdCallToAction());
                options.removeAllViews();
                options.addView(new AdOptionsView(activity, ad, layout), 0);

                java.util.List<View> clickable = new java.util.ArrayList<>();
                clickable.add(headline);
                clickable.add(cta);
                ad.registerViewForInteraction(content, media, icon, clickable);
                layout.setTag(R.id.native_ad_tag, ad);
                cb.onLoaded(layout);
            }

            @Override public void onAdClicked(Ad a) {}
            @Override public void onLoggingImpression(Ad a) {}
        }).build());
    }

    @Override
    public void destroyNative(View adView) {
        Object tag = adView.getTag(R.id.native_ad_tag);
        if (tag instanceof NativeAd) {
            ((NativeAd) tag).unregisterView();
            ((NativeAd) tag).destroy();
        }
    }
}

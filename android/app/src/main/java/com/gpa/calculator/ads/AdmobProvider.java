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
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.model.RemoteConfig;

/** Google AdMob. Uses Google's public test unit IDs whenever test mode is on in the admin panel. */
public final class AdmobProvider implements AdProvider {
    private static final String TAG = "AdmobProvider";
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110";

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

    // ---------------------------------------------------------------- native
    @Override
    public void loadNative(Activity activity, ViewGroup container, boolean compact, NativeCallback cb) {
        NativeAdOptions options = new NativeAdOptions.Builder()
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                .build();
        AdLoader loader = new AdLoader.Builder(activity, id(cfg.native_ad, TEST_NATIVE))
                .forNativeAd(ad -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        ad.destroy();
                        return;
                    }
                    NativeAdView v = (NativeAdView) android.view.LayoutInflater.from(activity).inflate(R.layout.native_ad_admob, container, false);
                    bind(v, ad, compact);
                    v.setTag(R.id.native_ad_tag, ad);
                    cb.onLoaded(v);
                })
                .withAdListener(new AdListener() {
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { cb.onFailed(e.getMessage()); }
                })
                .withNativeAdOptions(options)
                .build();
        loader.loadAd(request());
    }

    private static void bind(NativeAdView v, NativeAd ad, boolean compact) {
        android.widget.TextView headline = v.findViewById(R.id.ad_headline);
        android.widget.TextView advertiser = v.findViewById(R.id.ad_advertiser);
        android.widget.TextView body = v.findViewById(R.id.ad_body);
        android.widget.ImageView icon = v.findViewById(R.id.ad_icon);
        android.widget.Button cta = v.findViewById(R.id.ad_cta);
        MediaView media = v.findViewById(R.id.ad_media);

        headline.setText(ad.getHeadline());
        v.setHeadlineView(headline);

        String adv = ad.getAdvertiser() != null ? ad.getAdvertiser() : ad.getStore();
        advertiser.setText(adv == null ? "" : adv);
        advertiser.setVisibility(adv == null ? View.GONE : View.VISIBLE);
        v.setAdvertiserView(advertiser);

        if (ad.getBody() == null || compact) body.setVisibility(View.GONE);
        else body.setText(ad.getBody());
        v.setBodyView(body);

        if (ad.getIcon() != null) icon.setImageDrawable(ad.getIcon().getDrawable());
        else icon.setVisibility(View.GONE);
        v.setIconView(icon);

        if (ad.getCallToAction() == null) cta.setVisibility(View.INVISIBLE);
        else cta.setText(ad.getCallToAction());
        v.setCallToActionView(cta);

        if (compact) media.setVisibility(View.GONE);
        else v.setMediaView(media);

        v.setNativeAd(ad);
    }

    @Override
    public void destroyNative(View adView) {
        Object tag = adView.getTag(R.id.native_ad_tag);
        if (tag instanceof NativeAd) ((NativeAd) tag).destroy();
        if (adView instanceof NativeAdView) ((NativeAdView) adView).destroy();
    }
}

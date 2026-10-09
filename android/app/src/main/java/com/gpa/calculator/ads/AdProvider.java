package com.gpa.calculator.ads;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.gpa.calculator.data.remote.model.RemoteConfig;

/** One ad network. The AdsManager walks providers in the priority order set in the admin panel. */
public interface AdProvider {
    interface LoadCallback {
        void onLoaded();
        void onFailed(String reason);
    }

    interface BannerCallback {
        void onLoaded(View banner);
        void onFailed(String reason);
    }

    interface ShowCallback {
        void onDismissed();
        void onFailed(String reason);
    }

    interface RewardCallback extends ShowCallback {
        void onRewarded();
    }

    String name();

    /** Called once per process; must be idempotent. */
    void init(Context context, RemoteConfig.Network cfg, boolean testMode, Runnable onReady);

    boolean isInitialized();

    void loadBanner(Activity activity, ViewGroup container, BannerCallback cb);

    void destroyBanner(View banner);

    void loadInterstitial(Context context, LoadCallback cb);

    boolean isInterstitialReady();

    void showInterstitial(Activity activity, ShowCallback cb);

    void loadAppOpen(Context context, LoadCallback cb);

    boolean isAppOpenReady();

    void showAppOpen(Activity activity, ShowCallback cb);

    void loadRewarded(Context context, LoadCallback cb);

    boolean isRewardedReady();

    void showRewarded(Activity activity, RewardCallback cb);

    boolean supportsAppOpen();
}

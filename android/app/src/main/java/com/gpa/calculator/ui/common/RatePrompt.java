package com.gpa.calculator.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;

/** Google Play in-app review, shown once after N calculations (N from the admin panel). */
public final class RatePrompt {
    private RatePrompt() {}

    public static void maybeShow(Activity activity) {
        Prefs p = Prefs.get(activity);
        var f = ConfigStore.get(activity).config().features;
        if (!f.rate_prompt || p.ratePromptShown() || p.calcCount() < f.rate_prompt_after_calcs) return;
        p.setRatePromptShown();
        launch(activity);
    }

    /** Google's in-app review sheet; only for the automatic prompt (it may silently show nothing). */
    public static void launch(Activity activity) {
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !activity.isFinishing()) {
                ReviewInfo info = task.getResult();
                manager.launchReviewFlow(activity, info);
                Analytics.get(activity).log("rate_prompt");
            } else {
                openStore(activity);
            }
        });
    }

    /**
     * Opens this app's Play Store page. Use this for any "Rate this app" button: the in-app review
     * API (launch) is quota-limited, never shows for apps not installed from Play, and reports
     * success even when it shows nothing, so it must not sit behind a button.
     */
    public static void openStore(Activity activity) {
        String id = activity.getPackageName();
        Analytics.get(activity).log("rate_store_open");
        Intent play = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + id))
                .setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            activity.startActivity(play);
            return;
        } catch (Exception ignored) {
            // Play Store app missing or disabled
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + id)));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + id)));
            } catch (Exception ignored) {
                // no store and no browser: nothing to open
            }
        }
    }
}

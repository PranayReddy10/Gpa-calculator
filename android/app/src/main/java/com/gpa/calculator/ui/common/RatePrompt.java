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

    public static void openStore(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + activity.getPackageName())));
        } catch (Exception e) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + activity.getPackageName())));
        }
    }
}

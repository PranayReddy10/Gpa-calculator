package com.gpa.calculator.ui.calc;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;

import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.data.remote.Analytics;

/** Opens the right screen for a calculator with a container-transform shared element animation. */
public final class CalcLauncher {
    public static final String EXTRA_ID = "calc_id";
    public static final String EXTRA_INPUTS = "inputs_json";
    public static final String SHARED_NAME = "shared_element_container";

    private CalcLauncher() {}

    public static void open(Activity activity, @Nullable View sharedView, Calculator calc, @Nullable String inputsJson) {
        open(activity, sharedView, calc, inputsJson, true);
    }

    /** @param allowAd false skips the "on open" interstitial (push deep links, pop-up buttons). */
    public static void open(Activity activity, @Nullable View sharedView, Calculator calc, @Nullable String inputsJson, boolean allowAd) {
        Class<?> target;
        switch (calc.screen()) {
            case GRADES: target = GradeCalculatorActivity.class; break;
            case EMI: target = EmiActivity.class; break;
            case BASIC: target = BasicCalculatorActivity.class; break;
            case CURRENCY: target = CurrencyActivity.class; break;
            default: target = CalculatorActivity.class;
        }
        Intent i = new Intent(activity, target).putExtra(EXTRA_ID, calc.id());
        if (inputsJson != null) i.putExtra(EXTRA_INPUTS, inputsJson);
        Analytics.get(activity).log("calc_open", "calc", calc.id());
        Runnable go = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (sharedView != null && sharedView.isAttachedToWindow()) {
                ActivityOptions o = ActivityOptions.makeSceneTransitionAnimation(activity, sharedView, SHARED_NAME);
                activity.startActivity(i, o.toBundle());
            } else {
                activity.startActivity(i);
            }
        };
        if (allowAd) AdsManager.get(activity).onCalculatorOpen(activity, go);
        else go.run();
    }
}

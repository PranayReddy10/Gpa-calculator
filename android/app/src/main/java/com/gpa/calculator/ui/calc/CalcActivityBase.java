package com.gpa.calculator.ui.calc;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialContainerTransform;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.gpa.calculator.R;
import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.ui.BaseActivity;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.RatePrompt;

/** Shared plumbing for the three calculator screens: transitions, favourite menu, ads, counters. */
public abstract class CalcActivityBase extends BaseActivity {
    protected Calculator calc;
    protected boolean favourite;
    @Nullable private MenuItem favItem;
    @Nullable private AdsManager.BannerHandle banner;
    @Nullable private AdsManager.NativeHandle nativeAd;
    private boolean exiting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementEnterTransition(buildTransform(true));
        getWindow().setSharedElementReturnTransition(buildTransform(false));
        super.onCreate(savedInstanceState);
        findViewById(android.R.id.content).setTransitionName(CalcLauncher.SHARED_NAME);

        String id = getIntent().getStringExtra(CalcLauncher.EXTRA_ID);
        calc = id == null ? null : CalculatorRegistry.byId(id);
        if (calc == null) {
            finish();
            return;
        }
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { exitWithAd(); }
        });
        Repository.get(this).isFavourite(calc.id(), f -> {
            favourite = f;
            updateFavIcon(false);
        });
        Analytics.get(this).log("screen_view", "screen", calc.id());
    }

    private MaterialContainerTransform buildTransform(boolean entering) {
        MaterialContainerTransform t = new MaterialContainerTransform(this, entering);
        t.addTarget(android.R.id.content);
        t.setDuration(entering ? 420 : 320);
        t.setContainerColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0));
        return t;
    }

    protected void attachBanner(View container) {
        padBottomInset(container);
        banner = AdsManager.get(this).attachBanner(this, (android.view.ViewGroup) container, "calculator");
    }

    /** Native ad card for this calculator (hidden unless an ad loads). */
    protected void attachNative(android.view.ViewGroup container, boolean compact) {
        if (nativeAd != null) nativeAd.destroy();
        nativeAd = AdsManager.get(this).attachNative(this, container, calc.id(), compact);
    }

    /** Back / up: optionally shows the exit interstitial configured in the admin panel, then closes. */
    protected void exitWithAd() {
        if (exiting) return;
        exiting = true;
        AdsManager.get(this).onCalculatorExit(this, () -> {
            if (!isFinishing()) finishAfterTransition();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calculator, menu);
        favItem = menu.findItem(R.id.action_favourite);
        updateFavIcon(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_favourite) {
            favourite = !favourite;
            Repository.get(this).setFavourite(calc.id(), favourite);
            updateFavIcon(true);
            View v = findViewById(R.id.action_favourite);
            if (v != null) { Anim.pop(v); Haptics.tick(v); }
            return true;
        }
        if (item.getItemId() == R.id.action_reset) {
            onReset();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            exitWithAd();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateFavIcon(boolean animate) {
        if (favItem == null) return;
        favItem.setIcon(favourite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favItem.getIcon().setTint(favourite ? getColor(R.color.heart)
                : MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0));
    }

    /** Called after each successful calculation: counters, interstitial frequency cap, rating prompt. */
    protected void afterCalculation() {
        Prefs.get(this).incrementCalcCount();
        Analytics.get(this).log("calc_used", "calc", calc.id());
        com.gpa.calculator.push.PushManager.tagCalculation(this, calc.id());
        AdsManager.get(this).onCalculation(this, () -> RatePrompt.maybeShow(this));
    }

    protected abstract void onReset();

    @Override
    protected void onDestroy() {
        if (banner != null) banner.destroy();
        if (nativeAd != null) nativeAd.destroy();
        super.onDestroy();
    }
}

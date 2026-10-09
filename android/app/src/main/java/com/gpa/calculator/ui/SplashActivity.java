package com.gpa.calculator.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.ConfigStore;

/** Uses the AndroidX SplashScreen API; holds the splash briefly so the animated icon can play. */
public class SplashActivity extends BaseActivity {
    private boolean ready;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> !ready);
        ConfigStore.get(this).refreshIfStale();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ready = true;
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 650);
    }
}

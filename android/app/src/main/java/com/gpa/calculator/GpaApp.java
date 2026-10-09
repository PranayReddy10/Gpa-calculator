package com.gpa.calculator;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.material.color.DynamicColors;
import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.CloudSync;
import com.gpa.calculator.data.remote.ConfigStore;

public class GpaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs prefs = Prefs.get(this);
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode());
        if (prefs.dynamicColors()) DynamicColors.applyToActivitiesIfAvailable(this);

        prefs.firstOpenAt();
        ConfigStore.get(this).refreshIfStale();
        Analytics.get(this).registerDevice();
        AdsManager.get(this);

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(@NonNull LifecycleOwner owner) {
                prefs.incrementOpenCount();
                Analytics.get(GpaApp.this).log("app_open");
                ConfigStore.get(GpaApp.this).refreshIfStale();
                CloudSync.get(GpaApp.this).pushPending();
            }

            @Override public void onStop(@NonNull LifecycleOwner owner) {
                Analytics.get(GpaApp.this).flush();
            }
        });
    }
}

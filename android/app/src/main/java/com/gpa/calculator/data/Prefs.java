package com.gpa.calculator.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** Thin wrapper over SharedPreferences for app-level settings. */
public final class Prefs {
    private static final String FILE = "gpa_prefs";
    private static Prefs instance;
    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    // ---- identity
    public String deviceId() {
        String id = sp.getString("device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString("device_id", id).apply();
        }
        return id;
    }

    public String deviceToken() { return sp.getString("device_token", null); }
    public void setDeviceToken(String t) { sp.edit().putString("device_token", t).apply(); }

    // ---- theme: -1 follow system, 1 light, 2 dark (AppCompatDelegate constants)
    public int themeMode() { return sp.getInt("theme_mode", -1); }
    public void setThemeMode(int m) { sp.edit().putInt("theme_mode", m).apply(); }
    public boolean dynamicColors() { return sp.getBoolean("dynamic_colors", false); }
    public void setDynamicColors(boolean b) { sp.edit().putBoolean("dynamic_colors", b).apply(); }
    public boolean hapticFeedback() { return sp.getBoolean("haptics", true); }
    public void setHapticFeedback(boolean b) { sp.edit().putBoolean("haptics", b).apply(); }
    public boolean saveHistory() { return sp.getBoolean("save_history", true); }
    public void setSaveHistory(boolean b) { sp.edit().putBoolean("save_history", b).apply(); }
    public boolean cloudSync() { return sp.getBoolean("cloud_sync", true); }
    public void setCloudSync(boolean b) { sp.edit().putBoolean("cloud_sync", b).apply(); }
    public boolean analyticsOptIn() { return sp.getBoolean("analytics_opt_in", true); }
    public void setAnalyticsOptIn(boolean b) { sp.edit().putBoolean("analytics_opt_in", b).apply(); }

    // ---- counters used for ads / rating prompts
    public int calcCount() { return sp.getInt("calc_count", 0); }
    public int incrementCalcCount() {
        int v = calcCount() + 1;
        sp.edit().putInt("calc_count", v).apply();
        return v;
    }
    public long firstOpenAt() {
        long v = sp.getLong("first_open", 0);
        if (v == 0) {
            v = System.currentTimeMillis();
            sp.edit().putLong("first_open", v).apply();
        }
        return v;
    }
    public boolean isFirstOpen() { return !sp.contains("first_open"); }
    public int openCount() { return sp.getInt("open_count", 0); }
    public void incrementOpenCount() { sp.edit().putInt("open_count", openCount() + 1).apply(); }
    public boolean ratePromptShown() { return sp.getBoolean("rate_prompt_shown", false); }
    public void setRatePromptShown() { sp.edit().putBoolean("rate_prompt_shown", true).apply(); }
    public long adFreeUntil() { return sp.getLong("ad_free_until", 0); }
    public void setAdFreeUntil(long t) { sp.edit().putLong("ad_free_until", t).apply(); }
    public String seenAnnouncementId() { return sp.getString("seen_announcement", ""); }
    public void setSeenAnnouncementId(String id) { sp.edit().putString("seen_announcement", id).apply(); }
    public boolean onboardingDone() { return sp.getBoolean("onboarding_done", false); }
    public void setOnboardingDone() { sp.edit().putBoolean("onboarding_done", true).apply(); }

    // ---- generic
    public String getString(String k, String def) { return sp.getString(k, def); }
    public void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
    public long getLong(String k, long def) { return sp.getLong(k, def); }
    public void putLong(String k, long v) { sp.edit().putLong(k, v).apply(); }
}

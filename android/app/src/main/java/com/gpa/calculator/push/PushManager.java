package com.gpa.calculator.push;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.gpa.calculator.BuildConfig;
import com.gpa.calculator.R;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.data.remote.model.RemoteConfig;
import com.gpa.calculator.ui.MainActivity;
import com.gpa.calculator.ui.common.PopupDialog;
import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * OneSignal wrapper: init, user identity/tags, tap routing (deep links into calculators),
 * and a friendly pre-permission pop-up before Android's notification dialog.
 */
public final class PushManager {
    private static final String TAG = "PushManager";
    public static final String EXTRA_OPEN_CALC = "open_calc";
    public static final String EXTRA_OPEN_URL = "open_url";
    private static final long PROMPT_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int PROMPT_MAX_TIMES = 3;

    private static boolean initialized;

    private PushManager() {}

    public interface Result { void onResult(boolean granted); }

    /** OneSignal app ID: admin panel value (cached config) wins over the build-time property. */
    static String appId(Context c) {
        String remote = ConfigStore.get(c).config().app.onesignal_app_id;
        if (remote != null && !remote.trim().isEmpty()) return remote.trim();
        return BuildConfig.ONESIGNAL_APP_ID;
    }

    public static void init(Application app) {
        if (initialized) return;
        RemoteConfig cfg = ConfigStore.get(app).config();
        String id = appId(app);
        if (id == null || id.isEmpty() || !cfg.features.push_enabled) {
            Log.i(TAG, "OneSignal disabled (no app id or push_enabled=false)");
            return;
        }
        OneSignal.getDebug().setLogLevel(BuildConfig.DEBUG ? LogLevel.WARN : LogLevel.NONE);
        OneSignal.initWithContext(app, id);
        initialized = true;

        // Same anonymous id as our analytics, so admins can send a test push to one device.
        OneSignal.login(Prefs.get(app).deviceId());
        Map<String, String> tags = new HashMap<>();
        tags.put("app_version", String.valueOf(BuildConfig.VERSION_CODE));
        tags.put("calc_count", String.valueOf(Prefs.get(app).calcCount()));
        OneSignal.getUser().addTags(tags);

        if (!Prefs.get(app).pushEnabled()) OneSignal.getUser().getPushSubscription().optOut();
        OneSignal.getInAppMessages().setPaused(!cfg.features.in_app_messages);

        OneSignal.getNotifications().addClickListener(event -> {
            JSONObject data = event.getNotification().getAdditionalData();
            String calc = data == null ? null : data.optString("calc", null);
            String url = data == null ? null : data.optString("open_url", null);
            Analytics.get(app).log("push_opened", "id", String.valueOf(event.getNotification().getNotificationId()));
            Intent i = new Intent(app, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (calc != null && !calc.isEmpty()) i.putExtra(EXTRA_OPEN_CALC, calc);
            if (url != null && !url.isEmpty()) i.putExtra(EXTRA_OPEN_URL, url);
            app.startActivity(i);
        });
    }

    public static boolean isAvailable() { return initialized; }

    public static boolean hasPermission() {
        return initialized && OneSignal.getNotifications().getPermission();
    }

    /** Remember the user's choice and opt the OneSignal subscription in or out. */
    public static void setEnabled(Context c, boolean on) {
        Prefs.get(c).setPushEnabled(on);
        if (!initialized) return;
        if (on) OneSignal.getUser().getPushSubscription().optIn();
        else OneSignal.getUser().getPushSubscription().optOut();
    }

    /** Shows Android's permission dialog (OneSignal falls back to system settings if permanently denied). */
    public static void requestPermission(Activity activity, @Nullable Result cb) {
        if (!initialized) { if (cb != null) cb.onResult(false); return; }
        Handler main = new Handler(Looper.getMainLooper());
        OneSignal.getNotifications().requestPermission(true, Continue.with(r -> {
            boolean granted = r.isSuccess() && Boolean.TRUE.equals(r.getData());
            Analytics.get(activity).log(granted ? "push_permission_granted" : "push_permission_denied");
            if (granted) setEnabled(activity, true);
            main.post(() -> { if (cb != null) cb.onResult(granted); });
        }));
    }

    public static void openSystemSettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
        }
    }

    /**
     * Friendly soft prompt, shown at most 3 times, a week apart, after N app opens (admin panel).
     * @return true if the prompt was shown.
     */
    public static boolean maybeShowSoftPrompt(Activity activity) {
        RemoteConfig.Features f = ConfigStore.get(activity).config().features;
        Prefs p = Prefs.get(activity);
        if (!initialized || !f.push_prompt_enabled || hasPermission()) return false;
        if (p.openCount() < Math.max(1, f.push_prompt_after_opens)) return false;
        int times = (int) p.getLong("push_prompt_times", 0);
        long last = p.getLong("push_prompt_at", 0);
        if (times >= PROMPT_MAX_TIMES || System.currentTimeMillis() - last < PROMPT_COOLDOWN_MS) return false;
        p.putLong("push_prompt_times", times + 1);
        p.putLong("push_prompt_at", System.currentTimeMillis());
        Analytics.get(activity).log("push_prompt_shown");
        PopupDialog.with(activity)
                .icon(R.drawable.ic_notifications)
                .title(activity.getString(R.string.push_prompt_title))
                .body(activity.getString(R.string.push_prompt_body))
                .primary(activity.getString(R.string.push_prompt_allow), () -> requestPermission(activity, null))
                .secondary(activity.getString(R.string.push_prompt_later), () -> Analytics.get(activity).log("push_prompt_later"))
                .show();
        return true;
    }

    /** Keeps OneSignal tags in step with usage so the dashboard can build segments. */
    public static void tagCalculation(Context c, String calcId) {
        if (!initialized) return;
        Map<String, String> tags = new HashMap<>();
        tags.put("last_calc", calcId);
        tags.put("calc_count", String.valueOf(Prefs.get(c).calcCount()));
        OneSignal.getUser().addTags(tags);
    }
}

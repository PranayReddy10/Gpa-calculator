package com.gpa.calculator.data.remote;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gpa.calculator.BuildConfig;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.model.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight, privacy-friendly usage analytics sent to our own backend (no third-party SDK).
 * Events are queued in memory, persisted on flush failure, and sent in batches.
 */
public final class Analytics {
    private static final String TAG = "Analytics";
    private static final String QUEUE_KEY = "analytics_queue";
    private static final int FLUSH_AT = 20;
    private static Analytics instance;

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<JsonObject> queue = new ArrayList<>();
    private boolean registered;

    private Analytics(Context c) {
        app = c.getApplicationContext();
        registered = Prefs.get(app).deviceToken() != null;
        // restore persisted queue
        String saved = Prefs.get(app).getString(QUEUE_KEY, null);
        if (saved != null) {
            try {
                JsonArray arr = ApiClient.get(app).gson().fromJson(saved, JsonArray.class);
                for (int i = 0; i < arr.size(); i++) queue.add(arr.get(i).getAsJsonObject());
            } catch (Exception ignored) {
            }
            Prefs.get(app).putString(QUEUE_KEY, null);
        }
    }

    public static synchronized Analytics get(Context c) {
        if (instance == null) instance = new Analytics(c);
        return instance;
    }

    private boolean enabled() {
        return Prefs.get(app).analyticsOptIn() && ConfigStore.get(app).config().features.analytics
                && ApiClient.get(app).isConfigured();
    }

    public void log(String name) {
        log(name, (JsonObject) null);
    }

    public void log(String name, String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        log(name, o);
    }

    public void log(String name, String k, String v, String k2, String v2) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        o.addProperty(k2, v2);
        log(name, o);
    }

    public synchronized void log(String name, JsonObject params) {
        if (!enabled()) return;
        JsonObject e = new JsonObject();
        e.addProperty("name", name);
        e.addProperty("ts", System.currentTimeMillis() / 1000);
        if (params != null) e.add("params", params);
        queue.add(e);
        if (queue.size() >= FLUSH_AT) flush();
    }

    /** Registers / updates this device on the backend. Safe to call on every app start. */
    public void registerDevice() {
        if (!ApiClient.get(app).isConfigured()) return;
        io.execute(() -> {
            JsonObject body = deviceInfo();
            ApiResponse r = ApiClient.get(app).postSync("device/register", body);
            if (r != null && r.ok && r.data != null && r.data.isJsonObject()) {
                JsonObject d = r.data.getAsJsonObject();
                if (d.has("token")) {
                    Prefs.get(app).setDeviceToken(d.get("token").getAsString());
                    registered = true;
                }
            }
        });
    }

    public JsonObject deviceInfo() {
        JsonObject b = new JsonObject();
        b.addProperty("device_id", Prefs.get(app).deviceId());
        b.addProperty("manufacturer", Build.MANUFACTURER);
        b.addProperty("model", Build.MODEL);
        b.addProperty("os_version", Build.VERSION.RELEASE);
        b.addProperty("sdk_int", Build.VERSION.SDK_INT);
        b.addProperty("app_version", BuildConfig.VERSION_NAME);
        b.addProperty("app_version_code", BuildConfig.VERSION_CODE);
        Locale l = Resources.getSystem().getConfiguration().getLocales().get(0);
        b.addProperty("language", l.getLanguage());
        b.addProperty("country", l.getCountry());
        b.addProperty("timezone", java.util.TimeZone.getDefault().getID());
        b.addProperty("first_open", Prefs.get(app).firstOpenAt() / 1000);
        return b;
    }

    public synchronized void flush() {
        if (queue.isEmpty() || !enabled()) return;
        final List<JsonObject> batch = new ArrayList<>(queue);
        queue.clear();
        io.execute(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("device_id", Prefs.get(app).deviceId());
            body.addProperty("app_version_code", BuildConfig.VERSION_CODE);
            JsonArray arr = new JsonArray();
            for (JsonObject e : batch) arr.add(e);
            body.add("events", arr);
            ApiResponse r = ApiClient.get(app).postSync("events", body);
            if (r == null || !r.ok) {
                Log.i(TAG, "flush failed; persisting " + batch.size() + " events");
                synchronized (Analytics.this) {
                    batch.addAll(queue);
                    queue.clear();
                    queue.addAll(batch);
                    if (queue.size() > 200) queue.subList(0, queue.size() - 200).clear();
                    Prefs.get(app).putString(QUEUE_KEY, ApiClient.get(app).gson().toJson(queue));
                }
            }
        });
    }
}

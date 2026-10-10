package com.gpa.calculator.data.remote;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.model.RemoteConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches the remote config (ads + app flags) in SharedPreferences and refreshes it from the backend.
 * The admin panel edits this config; the app picks changes up on next launch / every 6 hours.
 */
public final class ConfigStore {
    private static final String TAG = "ConfigStore";
    private static final String KEY = "remote_config_json";
    private static final long REFRESH_MS = 6L * 60 * 60 * 1000;
    private static ConfigStore instance;

    public interface Listener {
        void onConfigChanged(RemoteConfig config);
    }

    private final Context app;
    private final Gson gson = new Gson();
    private RemoteConfig config;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean fetching;

    private ConfigStore(Context c) {
        app = c.getApplicationContext();
        String json = Prefs.get(app).getString(KEY, null);
        RemoteConfig parsed = null;
        if (json != null) {
            try {
                parsed = gson.fromJson(json, RemoteConfig.class);
            } catch (Exception e) {
                Log.w(TAG, "cached config invalid");
            }
        }
        config = parsed != null ? sanitize(parsed) : new RemoteConfig();
    }

    public static synchronized ConfigStore get(Context c) {
        if (instance == null) instance = new ConfigStore(c);
        return instance;
    }

    @NonNull
    public RemoteConfig config() { return config; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean isStale() {
        return System.currentTimeMillis() - config.fetched_at > REFRESH_MS;
    }

    public void refreshIfStale() {
        if (isStale()) refresh();
    }

    public void refresh() {
        if (fetching) return;
        fetching = true;
        ApiClient.get(app).get("config", new ApiClient.Result() {
            @Override public void onSuccess(com.google.gson.JsonElement data) {
                fetching = false;
                try {
                    RemoteConfig rc = gson.fromJson(data, RemoteConfig.class);
                    if (rc == null) return;
                    rc.fetched_at = System.currentTimeMillis();
                    config = sanitize(rc);
                    Prefs.get(app).putString(KEY, gson.toJson(config));
                    for (Listener l : new ArrayList<>(listeners)) l.onConfigChanged(config);
                } catch (Exception e) {
                    Log.w(TAG, "config parse failed: " + e.getMessage());
                }
            }

            @Override public void onError(String message) {
                fetching = false;
                Log.i(TAG, "config refresh failed: " + message);
            }
        });
    }

    /** Gson leaves missing nested objects null; replace with defaults so callers never NPE. */
    private static RemoteConfig sanitize(RemoteConfig rc) {
        RemoteConfig d = new RemoteConfig();
        if (rc.ads == null) rc.ads = d.ads;
        if (rc.ads.admob == null) rc.ads.admob = new RemoteConfig.Network();
        if (rc.ads.max == null) rc.ads.max = new RemoteConfig.Network();
        if (rc.ads.meta == null) rc.ads.meta = new RemoteConfig.Network();
        if (rc.ads.priority == null || rc.ads.priority.isEmpty()) rc.ads.priority = d.ads.priority;
        if (rc.app == null) rc.app = d.app;
        if (rc.app.popup == null) rc.app.popup = new RemoteConfig.Popup();
        if (rc.features == null) rc.features = d.features;
        return rc;
    }
}

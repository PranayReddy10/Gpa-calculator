package com.gpa.calculator.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.local.AppDatabase;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.data.remote.model.ApiResponse;
import com.gpa.calculator.data.remote.model.SavedCalc;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mirrors the user's saved calculations to the MySQL backend keyed by anonymous device token. */
public final class CloudSync {
    private static final String TAG = "CloudSync";
    private static CloudSync instance;

    public interface Callback {
        void onDone(boolean ok, int count, String message);
    }

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private CloudSync(Context c) { app = c.getApplicationContext(); }

    public static synchronized CloudSync get(Context c) {
        if (instance == null) instance = new CloudSync(c);
        return instance;
    }

    private boolean enabled() {
        return Prefs.get(app).cloudSync() && ConfigStore.get(app).config().features.cloud_save
                && ApiClient.get(app).isConfigured() && Prefs.get(app).deviceToken() != null;
    }

    /** Uploads every history row not yet on the server. Runs on the sync thread. */
    public void pushPending() {
        if (!enabled()) return;
        io.execute(() -> {
            List<HistoryEntity> pending = AppDatabase.get(app).history().unsynced();
            if (pending.isEmpty()) return;
            JsonArray items = new JsonArray();
            for (HistoryEntity e : pending) {
                JsonObject o = new JsonObject();
                o.addProperty("local_id", e.id);
                o.addProperty("calc_id", e.calcId);
                o.addProperty("title", e.title);
                o.addProperty("inputs", e.inputsJson);
                o.addProperty("results", e.resultsJson);
                o.addProperty("summary", e.summary);
                o.addProperty("created_at", e.createdAt / 1000);
                items.add(o);
            }
            JsonObject body = new JsonObject();
            body.add("items", items);
            ApiResponse r = ApiClient.get(app).postSync("saves", body);
            if (r != null && r.ok && r.data != null && r.data.isJsonArray()) {
                JsonArray arr = r.data.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    AppDatabase.get(app).history().markSynced(o.get("local_id").getAsLong(), o.get("id").getAsLong());
                }
                Log.i(TAG, "pushed " + arr.size() + " items");
            }
        });
    }

    /** Downloads everything stored on the server that is not present locally (restore after reinstall). */
    public void pull(Callback cb) {
        if (!enabled()) {
            cb.onDone(false, 0, "Cloud sync is off or backend not configured");
            return;
        }
        io.execute(() -> {
            ApiResponse r = ApiClient.get(app).getSync("saves");
            if (r == null || !r.ok || r.data == null || !r.data.isJsonArray()) {
                main.post(() -> cb.onDone(false, 0, r != null && r.error != null ? r.error : "Network error"));
                return;
            }
            int added = 0;
            SavedCalc[] rows = ApiClient.get(app).gson().fromJson(r.data, SavedCalc[].class);
            for (SavedCalc s : rows) {
                if (AppDatabase.get(app).history().countByRemoteId(s.id) > 0) continue;
                HistoryEntity e = new HistoryEntity();
                e.calcId = s.calc_id;
                e.title = s.title;
                e.inputsJson = s.inputs;
                e.resultsJson = s.results;
                e.summary = s.summary;
                e.createdAt = s.created_at * 1000;
                e.synced = true;
                e.remoteId = s.id;
                AppDatabase.get(app).history().insert(e);
                added++;
            }
            final int n = added;
            main.post(() -> cb.onDone(true, n, null));
        });
    }

    public void deleteRemote(long remoteId) {
        if (!enabled()) return;
        io.execute(() -> ApiClient.get(app).deleteSync("saves/" + remoteId));
    }

    public void deleteAllRemote() {
        if (!enabled()) return;
        io.execute(() -> ApiClient.get(app).deleteSync("saves"));
    }
}

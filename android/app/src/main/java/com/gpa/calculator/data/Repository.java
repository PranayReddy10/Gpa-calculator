package com.gpa.calculator.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.google.gson.Gson;
import com.gpa.calculator.core.CalcResult;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.data.local.AppDatabase;
import com.gpa.calculator.data.local.FavouriteEntity;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.CloudSync;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Facade over Room + cloud sync so UI code never touches threads directly. */
public final class Repository {
    private static Repository instance;
    private final AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final Context app;

    private Repository(Context c) {
        app = c.getApplicationContext();
        db = AppDatabase.get(app);
    }

    public static synchronized Repository get(Context c) {
        if (instance == null) instance = new Repository(c);
        return instance;
    }

    public ExecutorService io() { return io; }
    public AppDatabase db() { return db; }

    // ---- history
    public LiveData<List<HistoryEntity>> history() { return db.history().all(); }

    /** Newest history rows for the home screen's "Continue where you left off". */
    public LiveData<List<HistoryEntity>> recentHistory() { return db.history().recentLive(100); }

    public LiveData<List<HistoryEntity>> historyFor(String calcId) { return db.history().byCalc(calcId, 20); }

    public void saveHistory(Calculator calc, Map<String, String> inputs, List<CalcResult> results, Runnable onSaved) {
        if (!Prefs.get(app).saveHistory()) return;
        HistoryEntity e = new HistoryEntity();
        e.calcId = calc.id();
        e.title = calc.name();
        e.inputsJson = gson.toJson(inputs);
        e.resultsJson = gson.toJson(results);
        e.summary = results.isEmpty() ? "" : results.get(0).display;
        e.createdAt = System.currentTimeMillis();
        e.synced = false;
        io.execute(() -> {
            db.history().insert(e);
            if (onSaved != null) main.post(onSaved);
            CloudSync.get(app).pushPending();
        });
    }

    public void deleteHistory(HistoryEntity e) {
        io.execute(() -> {
            db.history().delete(e);
            if (e.remoteId > 0) CloudSync.get(app).deleteRemote(e.remoteId);
        });
    }

    public void clearHistory() {
        io.execute(() -> {
            db.history().deleteAll();
            CloudSync.get(app).deleteAllRemote();
        });
    }

    // ---- favourites
    public LiveData<List<String>> favouriteIds() { return db.favourites().idsLive(); }

    public LiveData<List<FavouriteEntity>> favourites() { return db.favourites().all(); }

    public void setFavourite(String calcId, boolean fav) {
        io.execute(() -> {
            if (fav) {
                FavouriteEntity e = new FavouriteEntity();
                e.calcId = calcId;
                e.addedAt = System.currentTimeMillis();
                db.favourites().insert(e);
                Analytics.get(app).log("favourite_add", "calc", calcId);
            } else {
                db.favourites().delete(calcId);
                Analytics.get(app).log("favourite_remove", "calc", calcId);
            }
        });
    }

    public void isFavourite(String calcId, Callback<Boolean> cb) {
        io.execute(() -> {
            boolean f = db.favourites().exists(calcId) > 0;
            main.post(() -> cb.onResult(f));
        });
    }

    public interface Callback<T> {
        void onResult(T value);
    }
}

package com.gpa.calculator.data.shop;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.gpa.calculator.data.remote.ApiClient;
import com.gpa.calculator.data.remote.model.ShopCatalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shop catalogue from GET /api/v1/shop, kept in memory and in a file so the shop opens
 * instantly (and offline) and refreshes in the background.
 */
public final class ShopRepo {
    private static final String TAG = "GPAShop";
    private static final String FILE = "shop_catalog.json";
    /** Refetch when the copy on the phone is older than this. The server also caches. */
    private static final long FRESH_MS = 15L * 60 * 1000;
    private static ShopRepo instance;

    public interface Callback {
        /** {@code fromNetwork} is false for the cached copy delivered first. */
        void onCatalog(ShopCatalog catalog, boolean fromNetwork);
        void onError(String message);
    }

    private final Context app;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    @Nullable private volatile ShopCatalog catalog;
    private long loadedAt;
    private boolean loading;
    private final List<Callback> waiting = new ArrayList<>();

    private ShopRepo(Context c) { app = c.getApplicationContext(); }

    public static synchronized ShopRepo get(Context c) {
        if (instance == null) instance = new ShopRepo(c);
        return instance;
    }

    /** The catalogue in memory right now (null before the first load). */
    @Nullable public ShopCatalog cached() { return catalog; }

    @Nullable
    public ShopCatalog.Product product(int id) {
        ShopCatalog c = catalog;
        if (c == null) return null;
        for (ShopCatalog.Product p : c.products) if (p.id == id) return p;
        return null;
    }

    /**
     * Delivers the cached catalogue straight away (if any), then a fresh one from the server
     * when the cached copy is old or {@code force} is set. Callbacks run on the main thread.
     */
    public void load(boolean force, Callback cb) {
        ShopCatalog mem = catalog;
        if (mem != null) cb.onCatalog(mem, false);
        boolean fresh = mem != null && System.currentTimeMillis() - loadedAt < FRESH_MS;
        if (fresh && !force) return;
        if (mem == null) {
            io.execute(() -> {
                ShopCatalog disk = readDisk();
                main.post(() -> {
                    if (disk != null && catalog == null) {
                        catalog = disk;
                        cb.onCatalog(disk, false);
                    }
                    fetch(cb);
                });
            });
        } else {
            fetch(cb);
        }
    }

    private void fetch(Callback cb) {
        waiting.add(cb);
        if (loading) return;
        loading = true;
        ApiClient.get(app).get("shop", new ApiClient.Result() {
            @Override public void onSuccess(JsonElement data) {
                loading = false;
                ShopCatalog c;
                try {
                    c = gson.fromJson(data, ShopCatalog.class);
                } catch (Exception e) {
                    deliverError("Could not read the shop");
                    return;
                }
                if (c == null || c.products == null) { deliverError("Could not read the shop"); return; }
                catalog = c;
                loadedAt = System.currentTimeMillis();
                String json = gson.toJson(c);
                io.execute(() -> writeDisk(json));
                List<Callback> cbs = new ArrayList<>(waiting);
                waiting.clear();
                for (Callback x : cbs) x.onCatalog(c, true);
            }

            @Override public void onError(String message) {
                loading = false;
                Log.w(TAG, "shop load failed: " + message);
                deliverError(message);
            }
        });
    }

    private void deliverError(String message) {
        List<Callback> cbs = new ArrayList<>(waiting);
        waiting.clear();
        for (Callback x : cbs) x.onError(message);
    }

    @Nullable
    private ShopCatalog readDisk() {
        File f = new File(app.getFilesDir(), FILE);
        if (!f.isFile()) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return gson.fromJson(r, ShopCatalog.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDisk(String json) {
        File tmp = new File(app.getFilesDir(), FILE + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(json);
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(app.getFilesDir(), FILE));
    }

    /** Called when the admin turns the shop off: forget the products on this phone. */
    public void clear() {
        catalog = null;
        loadedAt = 0;
        io.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            new File(app.getFilesDir(), FILE).delete();
        });
    }
}

package com.gpa.calculator.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.gpa.calculator.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Small image loader for the shop: images are decoded at the size they are shown, kept in a
 * memory cache, and stored on disk through OkHttp's HTTP cache (so they also show offline).
 */
public final class ImageLoader {
    private static ImageLoader instance;

    private final OkHttpClient http;
    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader(Context c) {
        File dir = new File(c.getCacheDir(), "images");
        http = new OkHttpClient.Builder()
                .cache(new Cache(dir, 40L * 1024 * 1024))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memory = new LruCache<String, Bitmap>(kb) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; }
        };
    }

    public static synchronized ImageLoader get(Context c) {
        if (instance == null) instance = new ImageLoader(c.getApplicationContext());
        return instance;
    }

    /** Loads {@code url} into {@code view}, decoded to about {@code targetPx} on its longer side. */
    public void load(ImageView view, @Nullable String url, int targetPx) {
        if (url == null || url.isEmpty()) {
            view.setTag(R.id.image_url, null);
            view.setImageDrawable(null);
            return;
        }
        String key = url + "#" + targetPx;
        view.setTag(R.id.image_url, key);
        Bitmap hit = memory.get(key);
        if (hit != null) {
            view.setImageBitmap(hit);
            view.setAlpha(1f);
            return;
        }
        view.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap bmp = fetch(url, targetPx);
            if (bmp != null) memory.put(key, bmp);
            main.post(() -> {
                if (!key.equals(view.getTag(R.id.image_url))) return; // row was recycled for another product
                if (bmp == null) return;
                view.setAlpha(0f);
                view.setImageBitmap(bmp);
                view.animate().alpha(1f).setDuration(180).start();
            });
        });
    }

    @Nullable
    private Bitmap fetch(String url, int targetPx) {
        byte[] bytes = download(url, false);
        if (bytes == null) bytes = download(url, true); // offline: whatever is in the disk cache
        if (bytes == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / (sample * 2) >= targetPx) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        try {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (OutOfMemoryError e) {
            memory.evictAll();
            return null;
        }
    }

    @Nullable
    private byte[] download(String url, boolean cacheOnly) {
        Request.Builder rb = new Request.Builder().url(url);
        // Product photos rarely change: reuse a stored copy for a week before asking again.
        rb.cacheControl(cacheOnly ? CacheControl.FORCE_CACHE
                : new CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build());
        try (Response r = http.newCall(rb.build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return null;
            return r.body().bytes();
        } catch (Exception e) {
            return null;
        }
    }
}

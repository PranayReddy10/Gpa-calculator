package com.gpa.calculator.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.gpa.calculator.core.Currencies;
import com.gpa.calculator.data.Prefs;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Exchange rates (USD base). Fetched from the URL set in the admin panel (default: Frankfurter /
 * European Central Bank, free, no key), cached in prefs, with built-in fallback rates for offline use.
 */
public final class RatesRepo {
    private static final String TAG = "RatesRepo";
    private static final String KEY_JSON = "rates_json";
    private static final String KEY_TS = "rates_ts";
    private static final long FRESH_MS = 6L * 60 * 60 * 1000;
    private static RatesRepo instance;

    public interface Callback {
        void onRates(Map<String, Double> rates, long updatedAt, boolean fromNetwork, @Nullable String error);
    }

    private final Context app;
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private RatesRepo(Context c) { app = c.getApplicationContext(); }

    public static synchronized RatesRepo get(Context c) {
        if (instance == null) instance = new RatesRepo(c);
        return instance;
    }

    public long updatedAt() { return Prefs.get(app).getLong(KEY_TS, 0); }

    /** Cached rates, or the built-in fallback table if nothing was ever fetched. */
    public Map<String, Double> cached() {
        String json = Prefs.get(app).getString(KEY_JSON, null);
        if (json != null) {
            Map<String, Double> m = parse(json);
            if (m != null) return m;
        }
        return new LinkedHashMap<>(Currencies.FALLBACK_USD);
    }

    public boolean isFresh() { return System.currentTimeMillis() - updatedAt() < FRESH_MS; }

    public void refresh(boolean force, Callback cb) {
        if (!force && isFresh()) {
            cb.onRates(cached(), updatedAt(), false, null);
            return;
        }
        String url = ConfigStore.get(app).config().features.currency_api_url;
        if (url == null || url.isEmpty()) url = "https://api.frankfurter.app/latest";
        final String finalUrl = url + (url.contains("?") ? "&" : "?") + "from=USD";
        new Thread(() -> {
            try (Response r = http.newCall(new Request.Builder().url(finalUrl).header("Accept", "application/json").build()).execute()) {
                String body = r.body() != null ? r.body().string() : "";
                Map<String, Double> m = r.isSuccessful() ? parse(body) : null;
                if (m == null || m.size() < 5) throw new IOException("Bad rates response " + r.code());
                long now = System.currentTimeMillis();
                Prefs.get(app).putString(KEY_JSON, body);
                Prefs.get(app).putLong(KEY_TS, now);
                main.post(() -> cb.onRates(m, now, true, null));
            } catch (Exception e) {
                Log.w(TAG, "refresh failed: " + e.getMessage());
                main.post(() -> cb.onRates(cached(), updatedAt(), false, e.getMessage()));
            }
        }, "rates").start();
    }

    /** Accepts {"base":"USD","rates":{...}} (Frankfurter / exchangerate.host style). */
    @Nullable
    private Map<String, Double> parse(String json) {
        try {
            JsonObject o = ApiClient.get(app).gson().fromJson(json, JsonObject.class);
            JsonObject rates = o.getAsJsonObject("rates");
            if (rates == null) return null;
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("USD", 1.0);
            for (String k : rates.keySet()) m.put(k, rates.get(k).getAsDouble());
            // keep extras the feed may not carry (e.g. AED/SAR are USD-pegged) so they stay selectable
            for (Map.Entry<String, Double> e : Currencies.FALLBACK_USD.entrySet()) if (!m.containsKey(e.getKey())) m.put(e.getKey(), e.getValue());
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.gpa.calculator.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gpa.calculator.BuildConfig;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.remote.model.ApiResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Minimal JSON client for the PHP backend in /backend. All callbacks run on the main thread. */
public final class ApiClient {
    private static final String TAG = "ApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static ApiClient instance;

    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context app;

    public interface Result {
        void onSuccess(JsonElement data);
        void onError(String message);
    }

    private ApiClient(Context c) {
        app = c.getApplicationContext();
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized ApiClient get(Context c) {
        if (instance == null) instance = new ApiClient(c);
        return instance;
    }

    public static String baseUrl() {
        String b = BuildConfig.API_BASE_URL;
        return b.endsWith("/") ? b : b + "/";
    }

    public boolean isConfigured() {
        return !baseUrl().contains("example.com");
    }

    public Gson gson() { return gson; }

    private Request.Builder builder(String path) {
        Request.Builder b = new Request.Builder()
                .url(baseUrl() + "api/v1/" + path)
                .header("X-App-Key", BuildConfig.APP_API_KEY)
                .header("X-Device-Id", Prefs.get(app).deviceId())
                .header("X-App-Version", String.valueOf(BuildConfig.VERSION_CODE))
                .header("Accept", "application/json");
        String token = Prefs.get(app).deviceToken();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    public void get(String path, @Nullable Result cb) {
        enqueue(builder(path).get().build(), cb);
    }

    public void post(String path, JsonObject body, @Nullable Result cb) {
        enqueue(builder(path).post(RequestBody.create(gson.toJson(body), JSON)).build(), cb);
    }

    public void delete(String path, @Nullable Result cb) {
        enqueue(builder(path).delete().build(), cb);
    }

    /** Blocking variant for use on background threads (sync / analytics flush). */
    @Nullable
    public ApiResponse postSync(String path, JsonObject body) {
        if (!isConfigured()) return null;
        try (Response r = http.newCall(builder(path).post(RequestBody.create(gson.toJson(body), JSON)).build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            return gson.fromJson(s, ApiResponse.class);
        } catch (Exception e) {
            Log.w(TAG, "postSync " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    public ApiResponse getSync(String path) {
        if (!isConfigured()) return null;
        try (Response r = http.newCall(builder(path).get().build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            return gson.fromJson(s, ApiResponse.class);
        } catch (Exception e) {
            Log.w(TAG, "getSync " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    public ApiResponse deleteSync(String path) {
        if (!isConfigured()) return null;
        try (Response r = http.newCall(builder(path).delete().build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            return gson.fromJson(s, ApiResponse.class);
        } catch (Exception e) {
            Log.w(TAG, "deleteSync " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    private void enqueue(Request req, @Nullable Result cb) {
        if (!isConfigured()) {
            if (cb != null) main.post(() -> cb.onError("Backend not configured"));
            return;
        }
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.w(TAG, req.url() + " failed: " + e.getMessage());
                if (cb != null) main.post(() -> cb.onError(e.getMessage() == null ? "Network error" : e.getMessage()));
            }

            @Override public void onResponse(Call call, Response response) {
                String s;
                try {
                    s = response.body() != null ? response.body().string() : "";
                } catch (IOException e) {
                    s = "";
                } finally {
                    response.close();
                }
                ApiResponse res;
                try {
                    res = gson.fromJson(s, ApiResponse.class);
                } catch (Exception e) {
                    res = null;
                }
                final ApiResponse fr = res;
                if (cb == null) return;
                main.post(() -> {
                    if (fr != null && fr.ok) cb.onSuccess(fr.data);
                    else cb.onError(fr != null && fr.error != null ? fr.error : "Server error " + response.code());
                });
            }
        });
    }
}

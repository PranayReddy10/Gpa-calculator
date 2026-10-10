package com.gpa.calculator.ui.common;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.gpa.calculator.databinding.DialogPopupBinding;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Animated, app-styled pop-up used for admin campaigns and the notification soft prompt. */
public final class PopupDialog {
    public interface Action { void run(); }

    private final Activity activity;
    private String title = "", body = "", primary = "OK", secondary = "";
    @Nullable private String imageUrl;
    @DrawableRes private int icon;
    @Nullable private Action onPrimary, onSecondary, onDismiss;

    private PopupDialog(Activity a) { activity = a; }

    public static PopupDialog with(Activity a) { return new PopupDialog(a); }

    public PopupDialog title(String t) { title = t == null ? "" : t; return this; }
    public PopupDialog body(String b) { body = b == null ? "" : b; return this; }
    public PopupDialog image(@Nullable String url) { imageUrl = url == null || url.trim().isEmpty() ? null : url.trim(); return this; }
    public PopupDialog icon(@DrawableRes int res) { icon = res; return this; }
    public PopupDialog primary(String label, @Nullable Action a) { primary = label; onPrimary = a; return this; }
    public PopupDialog secondary(String label, @Nullable Action a) { secondary = label == null ? "" : label; onSecondary = a; return this; }
    public PopupDialog onDismiss(@Nullable Action a) { onDismiss = a; return this; }

    /** Loads the image first (max ~3 s) so the pop-up never jumps in size after appearing. */
    public void show() {
        if (imageUrl == null) {
            present(null);
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        main.postDelayed(() -> { if (!done[0]) { done[0] = true; present(null); } }, 3000);
        new Thread(() -> {
            Bitmap bmp = download(imageUrl);
            main.post(() -> { if (!done[0]) { done[0] = true; present(bmp); } });
        }, "popup-image").start();
    }

    @Nullable
    private static Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            try (InputStream in = c.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void present(@Nullable Bitmap image) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogPopupBinding b = DialogPopupBinding.inflate(activity.getLayoutInflater());
        d.setContentView(b.getRoot());
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
        }

        if (image != null) {
            b.popupImage.setImageBitmap(image);
            b.popupImage.setVisibility(View.VISIBLE);
        } else if (icon != 0) {
            b.popupIcon.setImageResource(icon);
            b.popupIconBg.setVisibility(View.VISIBLE);
        }
        b.popupTitle.setText(title);
        b.popupTitle.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);
        b.popupBody.setText(body);
        b.popupBody.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        b.popupPrimary.setText(primary);
        b.popupSecondary.setText(secondary);
        b.popupSecondary.setVisibility(secondary.isEmpty() ? View.GONE : View.VISIBLE);

        final boolean[] handled = {false};
        b.popupPrimary.setOnClickListener(v -> { handled[0] = true; Haptics.confirm(v); d.dismiss(); if (onPrimary != null) onPrimary.run(); });
        b.popupSecondary.setOnClickListener(v -> { handled[0] = true; d.dismiss(); if (onSecondary != null) onSecondary.run(); });
        b.popupClose.setOnClickListener(v -> d.dismiss());
        d.setOnDismissListener(x -> { if (!handled[0] && onDismiss != null) onDismiss.run(); });

        // Entrance: scale + rise with overshoot, staggered content
        b.popupCard.setScaleX(0.85f);
        b.popupCard.setScaleY(0.85f);
        b.popupCard.setAlpha(0f);
        b.popupCard.setTranslationY(60f);
        b.popupCard.animate().scaleX(1f).scaleY(1f).alpha(1f).translationY(0f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
        Anim.staggerChildren(b.popupCard, 140);
        d.show();
    }
}

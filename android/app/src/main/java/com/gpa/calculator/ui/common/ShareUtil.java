package com.gpa.calculator.ui.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;

import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;
import com.gpa.calculator.R;
import com.gpa.calculator.core.CalcResult;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Copy / share helpers for results (text and rendered card image). */
public final class ShareUtil {
    private ShareUtil() {}

    public static String resultsAsText(String title, List<CalcResult> results) {
        StringBuilder sb = new StringBuilder(title).append('\n');
        for (CalcResult r : results) sb.append(r.label).append(": ").append(r.display).append('\n');
        return sb.toString().trim();
    }

    public static void copy(View anchor, String text) {
        ClipboardManager cm = (ClipboardManager) anchor.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("result", text));
        Snackbar.make(anchor, R.string.copied, Snackbar.LENGTH_SHORT).show();
    }

    public static void shareText(Context c, String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        c.startActivity(Intent.createChooser(i, c.getString(R.string.share)));
    }

    /** Renders the given view (the result card) to a PNG and shares it together with the text. */
    public static void shareViewAsImage(Context c, View view, String text) {
        try {
            Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            view.draw(canvas);
            File dir = new File(c.getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
            File f = new File(dir, "result.png");
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, text + "\n\n" + c.getString(R.string.result_share_footer))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(i, c.getString(R.string.share)));
        } catch (Exception e) {
            shareText(c, text);
        }
    }
}

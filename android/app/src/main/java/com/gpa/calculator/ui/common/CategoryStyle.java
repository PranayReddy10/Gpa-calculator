package com.gpa.calculator.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.gpa.calculator.R;
import com.gpa.calculator.core.Category;

/** One accent colour per category, used for icon circles and featured cards. */
public final class CategoryStyle {
    private CategoryStyle() {}

    @ColorInt
    public static int color(Context c, Category cat) {
        int res;
        switch (cat) {
            case ACADEMIC: res = R.color.cat_academic; break;
            case FINANCE: res = R.color.cat_finance; break;
            case EVERYDAY: res = R.color.cat_everyday; break;
            case CONVERTERS: res = R.color.cat_converters; break;
            default: res = R.color.cat_percentage;
        }
        return ContextCompat.getColor(c, res);
    }

    /** Tinted circle behind the icon + icon in the accent colour. */
    public static void applyIcon(View circle, ImageView icon, Category cat) {
        int color = color(circle.getContext(), cat);
        circle.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x29)));
        icon.setImageTintList(ColorStateList.valueOf(color));
    }

    /** Diagonal gradient from the accent colour to a deeper shade, rounded. */
    public static GradientDrawable gradient(Context c, Category cat, float radiusPx) {
        int color = color(c, cat);
        int deep = ColorUtils.blendARGB(color, 0xFF000000, 0.28f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, deep});
        g.setCornerRadius(radiusPx);
        return g;
    }
}

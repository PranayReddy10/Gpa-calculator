package com.gpa.calculator.ui.common;

import android.content.Context;

import androidx.annotation.DrawableRes;

import com.gpa.calculator.R;

/** Maps the engine's icon names (strings, so the engine stays Android-free) to drawable ids. */
public final class Icons {
    private Icons() {}

    @DrawableRes
    public static int resolve(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        return id != 0 ? id : R.drawable.ic_calculate;
    }
}

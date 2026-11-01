package com.gpa.calculator.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/** FrameLayout that is always as tall as it is wide (hosts the product photo gallery). */
public class SquareFrameLayout extends FrameLayout {
    public SquareFrameLayout(Context c) { super(c); }
    public SquareFrameLayout(Context c, @Nullable AttributeSet a) { super(c, a); }
    public SquareFrameLayout(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
    }
}

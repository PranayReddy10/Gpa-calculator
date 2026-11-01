package com.gpa.calculator.ui.common;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** ImageView that is always as tall as it is wide (product photos are square). */
public class SquareImageView extends AppCompatImageView {
    public SquareImageView(Context c) { super(c); }
    public SquareImageView(Context c, @Nullable AttributeSet a) { super(c, a); }
    public SquareImageView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
    }
}

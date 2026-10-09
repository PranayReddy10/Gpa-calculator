package com.gpa.calculator.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;
import com.gpa.calculator.core.Fmt;

/** TextView that animates from 0 to a numeric value with a prefix/suffix, or shows plain text. */
public class CountUpTextView extends MaterialTextView {
    @Nullable private ValueAnimator animator;

    public CountUpTextView(Context context) { super(context); }
    public CountUpTextView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
    public CountUpTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public void setPlain(CharSequence text) {
        cancel();
        setText(text);
    }

    public void countTo(double value, int decimals, String prefix, String suffix) {
        countTo(value, decimals, prefix, suffix, 900);
    }

    public void countTo(double value, int decimals, String prefix, String suffix, long duration) {
        cancel();
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 1e15) {
            setText(prefix + Fmt.fmt(value, decimals) + suffix);
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(2.2f));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            setText(prefix + Fmt.fmt(value * f, decimals) + suffix);
        });
        animator.start();
    }

    private void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }
}

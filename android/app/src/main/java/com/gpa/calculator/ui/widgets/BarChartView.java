package com.gpa.calculator.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.gpa.calculator.R;

/** Stacked bar chart (series A on top of series B) that grows upward with an overshoot animation. */
public class BarChartView extends View {
    private final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private double[] seriesA = new double[0];
    private double[] seriesB = new double[0];
    private String[] labels = new String[0];
    private float grow = 0f;
    @Nullable private ValueAnimator animator;

    public BarChartView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        int ca = 0xFFC8102E, cb = 0xFFF6A192;
        if (attrs != null) {
            TypedArray t = c.obtainStyledAttributes(attrs, R.styleable.BarChartView);
            ca = t.getColor(R.styleable.BarChartView_barColorA, ca);
            cb = t.getColor(R.styleable.BarChartView_barColorB, cb);
            t.recycle();
        }
        a.setColor(ca);
        b.setColor(cb);
        label.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setData(double[] seriesA, double[] seriesB, String[] labels) {
        this.seriesA = seriesA;
        this.seriesB = seriesB;
        this.labels = labels;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setInterpolator(new OvershootInterpolator(0.8f));
        animator.addUpdateListener(v -> {
            grow = (float) v.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = seriesA.length;
        if (n == 0) return;
        double max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, seriesA[i] + seriesB[i]);
        if (max <= 0) return;
        float labelH = label.getTextSize() + 8;
        float h = getHeight() - labelH;
        float slot = (float) getWidth() / n;
        float barW = slot * 0.6f;
        float radius = Math.min(6 * getResources().getDisplayMetrics().density, barW / 2);
        for (int i = 0; i < n; i++) {
            float x = slot * i + (slot - barW) / 2;
            float hb = (float) (seriesB[i] / max * h) * grow;
            float ha = (float) (seriesA[i] / max * h) * grow;
            r.set(x, h - hb, x + barW, h);
            canvas.drawRoundRect(r, radius, radius, b);
            r.set(x, h - hb - ha, x + barW, h - hb + radius);
            canvas.drawRoundRect(r, radius, radius, a);
            if (i < labels.length && (n <= 12 || i % Math.max(1, n / 10) == 0)) {
                canvas.drawText(labels[i], x + barW / 2, getHeight() - 2, label);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

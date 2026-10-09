package com.gpa.calculator.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.gpa.calculator.R;

/** Animated ring showing a fraction 0..1, drawn clockwise from the top. */
public class DonutChartView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction = 0f;
    private float shown = 0f;
    private String label = "";
    @Nullable private ValueAnimator animator;

    public DonutChartView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        int ringColor = 0x33FFFFFF, progressColor = Color.WHITE;
        float width = 10 * getResources().getDisplayMetrics().density;
        if (a != null) {
            TypedArray t = c.obtainStyledAttributes(a, R.styleable.DonutChartView);
            ringColor = t.getColor(R.styleable.DonutChartView_ringColor, ringColor);
            progressColor = t.getColor(R.styleable.DonutChartView_progressColor, progressColor);
            width = t.getDimension(R.styleable.DonutChartView_ringWidth, width);
            t.recycle();
        }
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(width);
        ring.setColor(ringColor);
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeWidth(width);
        progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(progressColor);
        text.setColor(progressColor);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
        text.setFakeBoldText(true);
    }

    /** @param fraction 0..1, @param label centre text (e.g. "72%") */
    public void animateTo(float fraction, String label) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.label = label;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(shown, this.fraction);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(v -> {
            shown = (float) v.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = ring.getStrokeWidth() / 2 + 2;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawOval(rect, ring);
        canvas.drawArc(rect, -90, 360 * shown, false, progress);
        if (!label.isEmpty()) {
            float y = getHeight() / 2f - (text.descent() + text.ascent()) / 2;
            canvas.drawText(label, getWidth() / 2f, y, text);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

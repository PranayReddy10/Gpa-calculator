package com.gpa.calculator.ui.common;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

/** Applies a font file to part of a text (TypefaceSpan(Typeface) needs API 28; the app supports 26). */
public class FontSpan extends MetricAffectingSpan {
    private final Typeface typeface;

    public FontSpan(@NonNull Typeface typeface) { this.typeface = typeface; }

    @Override public void updateDrawState(TextPaint p) { p.setTypeface(typeface); }

    @Override public void updateMeasureState(@NonNull TextPaint p) { p.setTypeface(typeface); }
}

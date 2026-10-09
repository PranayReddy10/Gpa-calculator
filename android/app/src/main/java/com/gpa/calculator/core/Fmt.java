package com.gpa.calculator.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting helpers shared by the engine and the UI. */
public final class Fmt {
    private Fmt() {}

    public static String fmt(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "—";
        BigDecimal bd = new BigDecimal(value).setScale(Math.max(0, decimals), RoundingMode.HALF_UP);
        bd = bd.stripTrailingZeros();
        if (bd.scale() < 0) bd = bd.setScale(0);
        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setGroupingUsed(true);
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(Math.max(0, decimals));
        // avoid "-0"
        if (bd.signum() == 0) bd = BigDecimal.ZERO;
        return df.format(bd);
    }

    public static String money(double value) {
        return fmt(value, 2);
    }

    public static String percent(double value) {
        return fmt(value, 2) + "%";
    }

    public static double round(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return value;
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}

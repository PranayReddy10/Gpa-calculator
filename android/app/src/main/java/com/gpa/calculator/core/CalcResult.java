package com.gpa.calculator.core;

/** One row of calculator output. Numeric results animate (count-up) in the UI. */
public final class CalcResult {
    public final String label;
    public final String display;
    public final Double numeric;   // null for text-only results
    public final String prefix;
    public final String suffix;
    public final int decimals;
    public final boolean primary;  // shown large at the top of the result card

    private CalcResult(String label, String display, Double numeric, String prefix, String suffix, int decimals, boolean primary) {
        this.label = label;
        this.display = display;
        this.numeric = numeric;
        this.prefix = prefix;
        this.suffix = suffix;
        this.decimals = decimals;
        this.primary = primary;
    }

    public static CalcResult num(String label, double value, int decimals) {
        return new CalcResult(label, Fmt.fmt(value, decimals), value, "", "", decimals, false);
    }

    public static CalcResult num(String label, double value, int decimals, String prefix, String suffix) {
        return new CalcResult(label, prefix + Fmt.fmt(value, decimals) + suffix, value, prefix, suffix, decimals, false);
    }

    public static CalcResult primary(String label, double value, int decimals, String prefix, String suffix) {
        return new CalcResult(label, prefix + Fmt.fmt(value, decimals) + suffix, value, prefix, suffix, decimals, true);
    }

    public static CalcResult text(String label, String value) {
        return new CalcResult(label, value, null, "", "", 0, false);
    }

    public static CalcResult primaryText(String label, String value) {
        return new CalcResult(label, value, null, "", "", 0, true);
    }

    @Override
    public String toString() {
        return label + ": " + display;
    }
}

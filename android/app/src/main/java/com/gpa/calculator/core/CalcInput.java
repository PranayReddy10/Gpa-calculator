package com.gpa.calculator.core;

/** Declarative description of one input field of a calculator. */
public final class CalcInput {
    public enum Type { NUMBER, INTEGER, SELECT, DATE, TEXT }

    public final String key;
    public final String label;
    public final Type type;
    public String hint = "";
    public String suffix = "";
    public String[] options = new String[0];
    public String defaultValue = "";
    public boolean optional = false;
    public double min = -Double.MAX_VALUE;
    public double max = Double.MAX_VALUE;

    private CalcInput(String key, String label, Type type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }

    public static CalcInput number(String key, String label) { return new CalcInput(key, label, Type.NUMBER); }
    public static CalcInput integer(String key, String label) { return new CalcInput(key, label, Type.INTEGER); }
    public static CalcInput text(String key, String label) { return new CalcInput(key, label, Type.TEXT); }
    public static CalcInput date(String key, String label) { return new CalcInput(key, label, Type.DATE); }
    public static CalcInput select(String key, String label, String... options) {
        CalcInput i = new CalcInput(key, label, Type.SELECT);
        i.options = options;
        i.defaultValue = options.length > 0 ? options[0] : "";
        return i;
    }

    public CalcInput hint(String h) { this.hint = h; return this; }
    public CalcInput suffix(String s) { this.suffix = s; return this; }
    public CalcInput def(String d) { this.defaultValue = d; return this; }
    public CalcInput optional() { this.optional = true; return this; }
    public CalcInput range(double min, double max) { this.min = min; this.max = max; return this; }
    public CalcInput positive() { this.min = 0; return this; }
}

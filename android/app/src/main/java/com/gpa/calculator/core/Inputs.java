package com.gpa.calculator.core;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed accessors over the raw String map that the UI collects. */
public final class Inputs {
    private final Map<String, String> raw;
    private final List<CalcInput> spec;

    public Inputs(Map<String, String> raw, List<CalcInput> spec) {
        this.raw = raw;
        this.spec = spec;
    }

    private CalcInput find(String key) {
        for (CalcInput i : spec) if (i.key.equals(key)) return i;
        return null;
    }

    public String str(String key) {
        String v = raw.get(key);
        return v == null ? "" : v.trim();
    }

    public boolean has(String key) {
        return !str(key).isEmpty();
    }

    public double num(String key) {
        CalcInput in = find(key);
        String s = str(key).replace(",", "");
        if (s.isEmpty()) {
            if (in != null && in.optional) return 0;
            if (in != null && !in.defaultValue.isEmpty()) s = in.defaultValue;
            else throw new CalcException(key, (in == null ? key : in.label) + " is required");
        }
        double v;
        try {
            v = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new CalcException(key, "Enter a valid number for " + (in == null ? key : in.label));
        }
        if (in != null) {
            if (v < in.min) throw new CalcException(key, (in.label) + " must be at least " + Fmt.fmt(in.min, 2));
            if (v > in.max) throw new CalcException(key, (in.label) + " must be at most " + Fmt.fmt(in.max, 2));
        }
        return v;
    }

    public double num(String key, double def) {
        return has(key) ? num(key) : def;
    }

    public int integer(String key) {
        double v = num(key);
        if (v != Math.floor(v)) {
            CalcInput in = find(key);
            throw new CalcException(key, (in == null ? key : in.label) + " must be a whole number");
        }
        return (int) v;
    }

    public String select(String key) {
        CalcInput in = find(key);
        String s = str(key);
        if (s.isEmpty() && in != null) s = in.defaultValue;
        return s;
    }

    public int selectIndex(String key) {
        CalcInput in = find(key);
        String s = select(key);
        if (in != null) for (int i = 0; i < in.options.length; i++) if (in.options[i].equals(s)) return i;
        return 0;
    }

    public LocalDate date(String key) {
        CalcInput in = find(key);
        String s = str(key);
        if (s.isEmpty()) {
            if (in != null && in.optional) return null;
            throw new CalcException(key, (in == null ? key : in.label) + " is required");
        }
        try {
            return LocalDate.parse(s); // ISO yyyy-MM-dd
        } catch (DateTimeParseException e) {
            throw new CalcException(key, "Enter a valid date for " + (in == null ? key : in.label));
        }
    }

    /** Parses a comma / space / newline separated list of numbers. */
    public List<Double> numberList(String key) {
        CalcInput in = find(key);
        String s = str(key);
        List<Double> out = new ArrayList<>();
        for (String part : s.split("[,\\s;]+")) {
            if (part.isEmpty()) continue;
            try {
                out.add(Double.parseDouble(part));
            } catch (NumberFormatException e) {
                throw new CalcException(key, "'" + part + "' is not a number");
            }
        }
        if (out.isEmpty()) throw new CalcException(key, (in == null ? key : in.label) + " is required");
        return out;
    }
}

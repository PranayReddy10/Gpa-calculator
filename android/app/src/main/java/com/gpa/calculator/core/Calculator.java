package com.gpa.calculator.core;

import java.util.List;
import java.util.Map;

/**
 * A calculator definition: metadata, declared inputs and the compute function.
 * Implementations must be pure (no Android dependencies) so they can be unit tested on the JVM.
 */
public abstract class Calculator {
    /** Which screen renders this calculator. */
    public enum Screen { GENERIC, GRADES, EMI, BASIC, CURRENCY }

    public abstract String id();
    public abstract String name();
    public abstract String description();
    public abstract Category category();
    /** Drawable resource name, resolved by the UI layer. */
    public abstract String icon();
    public abstract List<CalcInput> inputs();
    public abstract List<CalcResult> compute(Inputs in);

    public Screen screen() { return Screen.GENERIC; }

    /** Short formula / explanation shown under the result card. */
    public String formula() { return ""; }

    /** Keywords used by home-screen search, in addition to name and description. */
    public String keywords() { return ""; }

    public final List<CalcResult> compute(Map<String, String> raw) {
        return compute(new Inputs(raw, inputs()));
    }

    public final boolean matches(String query) {
        String q = query.toLowerCase();
        return name().toLowerCase().contains(q)
                || description().toLowerCase().contains(q)
                || keywords().toLowerCase().contains(q)
                || category().title.toLowerCase().contains(q);
    }
}

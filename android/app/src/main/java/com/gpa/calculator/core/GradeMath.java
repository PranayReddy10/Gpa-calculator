package com.gpa.calculator.core;

import java.util.List;

/** Shared grade-point maths used by the SGPA / CGPA screens. */
public final class GradeMath {
    private GradeMath() {}

    /** SGPA = Σ(credit × gradePoint) / Σ(credit). */
    public static double sgpa(List<Double> credits, List<Double> points) {
        if (credits.size() != points.size() || credits.isEmpty()) throw new CalcException("Add at least one subject");
        double num = 0, den = 0;
        for (int i = 0; i < credits.size(); i++) {
            double c = credits.get(i), p = points.get(i);
            if (c < 0) throw new CalcException("Credits cannot be negative");
            if (p < 0) throw new CalcException("Grade points cannot be negative");
            num += c * p;
            den += c;
        }
        if (den == 0) throw new CalcException("Total credits must be greater than 0");
        return num / den;
    }

    /** CGPA = Σ(SGPA × semester credits) / Σ(semester credits). */
    public static double cgpa(List<Double> sgpas, List<Double> credits) {
        return sgpa(credits, sgpas);
    }

    public static double gpaToPercent(double gpa, double scaleMax, double multiplier) {
        if (gpa < 0 || gpa > scaleMax) throw new CalcException("GPA must be between 0 and " + Fmt.fmt(scaleMax, 1));
        return Math.min(100, gpa * multiplier);
    }
}

package com.gpa.calculator.core;

import java.util.ArrayList;
import java.util.List;

/** Letter-grade scales used by the SGPA-with-grade calculator. */
public final class GradeScale {
    public final String name;
    public final String[] letters;
    public final double[] points;
    public final double maxPoints;

    private GradeScale(String name, double maxPoints, String[] letters, double[] points) {
        this.name = name;
        this.maxPoints = maxPoints;
        this.letters = letters;
        this.points = points;
    }

    public static final GradeScale TEN_POINT = new GradeScale("10-point (O, A+, A, B+, B, C, P, F)", 10,
            new String[]{"O", "A+", "A", "B+", "B", "C", "P", "F"},
            new double[]{10, 9, 8, 7, 6, 5, 4, 0});

    public static final GradeScale TEN_POINT_S = new GradeScale("10-point (S, A, B, C, D, E, F)", 10,
            new String[]{"S", "A", "B", "C", "D", "E", "F"},
            new double[]{10, 9, 8, 7, 6, 5, 0});

    public static final GradeScale FOUR_POINT = new GradeScale("4-point (A, A-, B+, B, B-, C+, C, D, F)", 4,
            new String[]{"A", "A-", "B+", "B", "B-", "C+", "C", "D", "F"},
            new double[]{4.0, 3.7, 3.3, 3.0, 2.7, 2.3, 2.0, 1.0, 0.0});

    public static final GradeScale FIVE_POINT = new GradeScale("5-point (A, B, C, D, E, F)", 5,
            new String[]{"A", "B", "C", "D", "E", "F"},
            new double[]{5, 4, 3, 2, 1, 0});

    public static final List<GradeScale> ALL = new ArrayList<>();

    static {
        ALL.add(TEN_POINT);
        ALL.add(TEN_POINT_S);
        ALL.add(FOUR_POINT);
        ALL.add(FIVE_POINT);
    }

    public static String[] names() {
        String[] n = new String[ALL.size()];
        for (int i = 0; i < n.length; i++) n[i] = ALL.get(i).name;
        return n;
    }

    public static GradeScale byName(String name) {
        for (GradeScale s : ALL) if (s.name.equals(name)) return s;
        return TEN_POINT;
    }

    public double pointsFor(String letter) {
        for (int i = 0; i < letters.length; i++) if (letters[i].equalsIgnoreCase(letter)) return points[i];
        throw new CalcException("Unknown grade: " + letter);
    }

    /** Maps a percentage to a letter on this scale (common Indian 10-point mapping or a 4-point mapping). */
    public static String letterForPercent(double pct) {
        if (pct >= 90) return "O";
        if (pct >= 80) return "A+";
        if (pct >= 70) return "A";
        if (pct >= 60) return "B+";
        if (pct >= 50) return "B";
        if (pct >= 45) return "C";
        if (pct >= 40) return "P";
        return "F";
    }

    public static String remarkForPercent(double pct) {
        if (pct >= 90) return "Outstanding";
        if (pct >= 80) return "Excellent";
        if (pct >= 70) return "Very good";
        if (pct >= 60) return "Good";
        if (pct >= 50) return "Average";
        if (pct >= 40) return "Pass";
        return "Fail";
    }
}

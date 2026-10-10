package com.gpa.calculator.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/** "My semesters": SGPA results saved from the SGPA screens and reused by the CGPA screen. */
public final class Semesters {
    private Semesters() {}

    public static final String PREF_KEY = "my_semesters";
    public static final int MAX = 16;

    public static final class Semester {
        public String name;
        public double sgpa;
        public double credits;

        public Semester() {}

        public Semester(String name, double sgpa, double credits) {
            this.name = name;
            this.sgpa = sgpa;
            this.credits = credits;
        }
    }

    private static final Gson GSON = new Gson();

    public static List<Semester> parse(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            List<Semester> l = GSON.fromJson(json, new TypeToken<List<Semester>>() {}.getType());
            List<Semester> out = new ArrayList<>();
            if (l != null) for (Semester s : l) if (s != null && s.credits > 0 && s.sgpa >= 0) out.add(s);
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String toJson(List<Semester> list) {
        return GSON.toJson(list);
    }

    /** Adds or replaces (same name, case-insensitive) and keeps at most {@link #MAX}. */
    public static List<Semester> upsert(List<Semester> list, Semester s) {
        List<Semester> out = new ArrayList<>();
        boolean replaced = false;
        for (Semester x : list) {
            if (x.name != null && x.name.equalsIgnoreCase(s.name)) {
                out.add(s);
                replaced = true;
            } else {
                out.add(x);
            }
        }
        if (!replaced) out.add(s);
        while (out.size() > MAX) out.remove(0);
        return out;
    }

    public static String nextName(List<Semester> list) {
        return "Semester " + (list.size() + 1);
    }
}

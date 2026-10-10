package com.gpa.calculator.core;

import com.gpa.calculator.core.calculators.AcademicCalculators;
import com.gpa.calculator.core.calculators.CompareCalculators;
import com.gpa.calculator.core.calculators.ConverterCalculators;
import com.gpa.calculator.core.calculators.EverydayCalculators;
import com.gpa.calculator.core.calculators.FinanceCalculators;
import com.gpa.calculator.core.calculators.PercentageCalculators;
import com.gpa.calculator.core.calculators.ToolCalculators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single source of truth for every calculator in the app. */
public final class CalculatorRegistry {
    private static final List<Calculator> ALL;
    private static final Map<String, Calculator> BY_ID = new LinkedHashMap<>();

    static {
        List<Calculator> list = new ArrayList<>();
        list.add(ToolCalculators.basic());
        list.addAll(PercentageCalculators.all());
        list.addAll(AcademicCalculators.all());
        list.addAll(FinanceCalculators.all());
        list.add(new CompareCalculators.LoanCompare());
        list.addAll(EverydayCalculators.all());
        list.add(new CompareCalculators.UnitPrice());
        list.add(ToolCalculators.currency());
        list.addAll(ConverterCalculators.all());
        for (Calculator c : list) {
            if (BY_ID.put(c.id(), c) != null) throw new IllegalStateException("Duplicate calculator id " + c.id());
        }
        ALL = Collections.unmodifiableList(list);
    }

    private CalculatorRegistry() {}

    public static List<Calculator> all() { return ALL; }

    public static Calculator byId(String id) { return BY_ID.get(id); }

    public static List<Calculator> byCategory(Category category) {
        List<Calculator> out = new ArrayList<>();
        for (Calculator c : ALL) if (c.category() == category) out.add(c);
        return out;
    }

    public static List<Calculator> search(String query, Category category) {
        List<Calculator> out = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        for (Calculator c : ALL) {
            if (category != null && c.category() != category) continue;
            if (!q.isEmpty() && !c.matches(q)) continue;
            out.add(c);
        }
        return out;
    }

    public static int count() { return ALL.size(); }
}

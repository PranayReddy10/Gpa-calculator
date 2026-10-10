package com.gpa.calculator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gpa.calculator.data.Semesters;
import com.gpa.calculator.ui.home.HistoryGrouping;
import com.gpa.calculator.ui.home.HomeSections;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewFeaturesTest {
    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    // ---------------------------------------------------------------- CGPA target
    @Test public void cgpaTargetRequired() {
        Calculator c = CalculatorRegistry.byId("cgpa_target");
        // 7.5 over 80 credits, want 8.0 over 120 -> (8*120 - 7.5*80)/40 = 9.0
        List<CalcResult> r = c.compute(map("current", "7.5", "done", "80", "target", "8", "remaining", "40"));
        assertEquals(9.0, r.get(0).numeric, 1e-9);
        assertEquals("Ambitious — needs a big jump", r.get(1).display);
        assertEquals((7.5 * 80 + 400) / 120, r.get(2).numeric, 1e-9);
    }

    @Test public void cgpaTargetEdges() {
        Calculator c = CalculatorRegistry.byId("cgpa_target");
        // ahead of target: only 7.0 needed in the remaining credits
        List<CalcResult> r = c.compute(map("current", "8.5", "done", "80", "target", "8", "remaining", "40"));
        assertEquals(7.0, r.get(0).numeric, 1e-9);
        assertEquals("Keep your current pace", r.get(1).display);
        // so far ahead that even 0 in the rest keeps the target
        assertEquals("Already achieved", c.compute(map("current", "9.5", "done", "100", "target", "7", "remaining", "10")).get(0).display);
        assertEquals("Not reachable", c.compute(map("current", "6", "done", "120", "target", "9", "remaining", "20")).get(0).display);
    }

    // ---------------------------------------------------------------- Home sections
    private static List<HomeSections.Item.Type> types(List<HomeSections.Item> items) {
        List<HomeSections.Item.Type> t = new ArrayList<>();
        for (HomeSections.Item i : items) t.add(i.type);
        return t;
    }

    @Test public void homeFreshInstall() {
        List<HomeSections.Item> items = HomeSections.build("", null, Collections.emptyList(), Collections.emptyList());
        assertEquals(HomeSections.Item.Type.SECTION, items.get(0).type);
        assertEquals("Popular", items.get(0).title);
        assertEquals(HomeSections.Item.Type.FEATURED_ROW, items.get(1).type);
        assertEquals(6, items.get(1).calcs.size());
        int calcs = 0;
        for (HomeSections.Item i : items) if (i.type == HomeSections.Item.Type.CALC) calcs++;
        assertEquals(CalculatorRegistry.count(), calcs);
    }

    @Test public void homeWithRecentsAndFavourites() {
        Calculator emi = CalculatorRegistry.byId("emi"), sgpa = CalculatorRegistry.byId("sgpa");
        List<HomeSections.Recent> recents = Arrays.asList(
                new HomeSections.Recent(emi, "8,678", "{}", 3),
                new HomeSections.Recent(sgpa, "8.9", "{}", 2),
                new HomeSections.Recent(emi, "9,000", "{}", 1)); // duplicate -> dropped
        List<HomeSections.Item> items = HomeSections.build("", null, recents, Arrays.asList("cgpa", "nope"));
        assertEquals(Arrays.asList(HomeSections.Item.Type.SECTION, HomeSections.Item.Type.RECENT_ROW,
                HomeSections.Item.Type.SECTION, HomeSections.Item.Type.FAVOURITE_ROW,
                HomeSections.Item.Type.SECTION, HomeSections.Item.Type.FEATURED_ROW), types(items).subList(0, 6));
        assertEquals(2, items.get(1).recents.size());
        assertEquals("8,678", items.get(1).recents.get(0).summary);
        assertEquals(1, items.get(3).calcs.size()); // unknown id skipped
        // first category section offers "See all"
        assertEquals(Category.values()[0], items.get(6).seeAll);
    }

    @Test public void homeSearchAndCategory() {
        List<HomeSections.Item> items = HomeSections.build("loan", null, Collections.emptyList(), Collections.emptyList());
        assertTrue(items.get(0).title.endsWith("results"));
        assertTrue(items.size() >= 3);
        for (HomeSections.Item i : items.subList(1, items.size())) assertEquals(HomeSections.Item.Type.CALC, i.type);
        items = HomeSections.build("", Category.FINANCE, Collections.emptyList(), Collections.emptyList());
        assertEquals("Finance", items.get(0).title);
        assertEquals(CalculatorRegistry.byCategory(Category.FINANCE).size() + 1, items.size());
        items = HomeSections.build("zzzz", null, Collections.emptyList(), Collections.emptyList());
        assertEquals("No matches", items.get(0).title);
        assertEquals(1, items.size());
        assertNull(items.get(0).seeAll);
    }

    @Test public void greeting() {
        assertEquals("Good morning", HomeSections.greeting(7));
        assertEquals("Good afternoon", HomeSections.greeting(13));
        assertEquals("Good evening", HomeSections.greeting(19));
        assertEquals("Hello, night owl", HomeSections.greeting(2));
    }

    // ---------------------------------------------------------------- History grouping
    private static long at(String d, int h) {
        return LocalDate.parse(d).atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test public void historyLabels() {
        java.util.Locale.setDefault(java.util.Locale.US);
        long now = at("2026-10-15", 12);
        assertEquals("Today", HistoryGrouping.label(at("2026-10-15", 1), now, ZoneOffset.UTC));
        assertEquals("Yesterday", HistoryGrouping.label(at("2026-10-14", 23), now, ZoneOffset.UTC));
        assertEquals("Earlier this week", HistoryGrouping.label(at("2026-10-10", 9), now, ZoneOffset.UTC));
        assertEquals("October 2026", HistoryGrouping.label(at("2026-10-01", 9), now, ZoneOffset.UTC));
        assertEquals("December 2025", HistoryGrouping.label(at("2025-12-31", 9), now, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- Semesters
    @Test public void semesters() {
        List<Semesters.Semester> l = Semesters.parse(null);
        assertEquals("Semester 1", Semesters.nextName(l));
        l = Semesters.upsert(l, new Semesters.Semester("Semester 1", 8.2, 20));
        l = Semesters.upsert(l, new Semesters.Semester("Semester 2", 8.8, 22));
        l = Semesters.upsert(l, new Semesters.Semester("semester 1", 8.5, 20)); // replaces, case-insensitive
        assertEquals(2, l.size());
        assertEquals(8.5, l.get(0).sgpa, 0);
        List<Semesters.Semester> back = Semesters.parse(Semesters.toJson(l));
        assertEquals(2, back.size());
        assertEquals("Semester 2", back.get(1).name);
        assertEquals(0, Semesters.parse("not json").size());
        assertEquals(1, Semesters.parse("[{\"name\":\"a\",\"sgpa\":8,\"credits\":20},{\"name\":\"bad\",\"sgpa\":8,\"credits\":0}]").size());
        for (int i = 0; i < 20; i++) l = Semesters.upsert(l, new Semesters.Semester("S" + i, 7, 20));
        assertEquals(Semesters.MAX, l.size());
    }
}

package com.gpa.calculator.ui.home;

import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.core.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds the home screen's sectioned list. Pure Java so the rules are unit tested. */
public final class HomeSections {
    private HomeSections() {}

    public static final List<String> FEATURED = Collections.unmodifiableList(Arrays.asList(
            "sgpa_grade", "cgpa", "marks_percent", "emi", "basic", "currency"));
    public static final int MAX_RECENT = 10;

    /** A calculator the user ran recently, with the inputs to reopen it exactly as they left it. */
    public static final class Recent {
        public final Calculator calc;
        public final String summary;
        public final String inputsJson;
        public final long at;

        public Recent(Calculator calc, String summary, String inputsJson, long at) {
            this.calc = calc;
            this.summary = summary == null ? "" : summary;
            this.inputsJson = inputsJson;
            this.at = at;
        }
    }

    public static final class Item {
        public enum Type { SECTION, RECENT_ROW, FAVOURITE_ROW, FEATURED_ROW, CALC }

        public final Type type;
        public final String key;
        public String title = "";
        public String subtitle = "";
        /** For SECTION: the category "See all" selects (null = no button). */
        public Category seeAll;
        public Calculator calc;
        public List<Calculator> calcs = Collections.emptyList();
        public List<Recent> recents = Collections.emptyList();

        Item(Type type, String key) {
            this.type = type;
            this.key = key;
        }

        /** Same identity and same visible content (used by DiffUtil). */
        public boolean sameContent(Item o) {
            if (type != o.type || !key.equals(o.key) || !title.equals(o.title) || !subtitle.equals(o.subtitle)) return false;
            if (type == Type.RECENT_ROW) {
                if (recents.size() != o.recents.size()) return false;
                for (int i = 0; i < recents.size(); i++) {
                    Recent a = recents.get(i), b = o.recents.get(i);
                    if (a.at != b.at || !a.calc.id().equals(b.calc.id())) return false;
                }
                return true;
            }
            if (type == Type.FAVOURITE_ROW || type == Type.FEATURED_ROW) return ids(calcs).equals(ids(o.calcs));
            return true;
        }

        private static List<String> ids(List<Calculator> l) {
            List<String> out = new ArrayList<>();
            for (Calculator c : l) out.add(c.id());
            return out;
        }
    }

    /**
     * @param recentsNewestFirst history entries newest first; may repeat calculators (de-duplicated here)
     * @param favouriteIds favourite calculator ids, most recently added first
     */
    public static List<Item> build(String query, Category category, List<Recent> recentsNewestFirst, List<String> favouriteIds) {
        List<Item> out = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty() || category != null) {
            List<Calculator> list = CalculatorRegistry.search(q, category);
            Item h = new Item(Item.Type.SECTION, "results");
            h.title = !q.isEmpty() ? (list.isEmpty() ? "No matches" : list.size() + (list.size() == 1 ? " result" : " results"))
                    : category.title;
            h.subtitle = q.isEmpty() ? list.size() + " calculators" : (category != null ? "in " + category.title : "");
            out.add(h);
            for (Calculator c : list) out.add(calc(c));
            return out;
        }

        // Continue where you left off
        List<Recent> recents = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Recent r : recentsNewestFirst) {
            if (r.calc == null || !seen.add(r.calc.id())) continue;
            recents.add(r);
            if (recents.size() == MAX_RECENT) break;
        }
        if (!recents.isEmpty()) {
            out.add(section("recent", "Continue where you left off", "", null));
            Item row = new Item(Item.Type.RECENT_ROW, "recent_row");
            row.recents = recents;
            out.add(row);
        }

        List<Calculator> favs = new ArrayList<>();
        for (String id : favouriteIds) {
            Calculator c = CalculatorRegistry.byId(id);
            if (c != null) favs.add(c);
        }
        if (!favs.isEmpty()) {
            out.add(section("favs", "Favourites", favs.size() + "", null));
            Item row = new Item(Item.Type.FAVOURITE_ROW, "fav_row");
            row.calcs = favs;
            out.add(row);
        }

        List<Calculator> featured = new ArrayList<>();
        for (String id : FEATURED) {
            Calculator c = CalculatorRegistry.byId(id);
            if (c != null) featured.add(c);
        }
        out.add(section("popular", "Popular", "", null));
        Item fr = new Item(Item.Type.FEATURED_ROW, "featured_row");
        fr.calcs = featured;
        out.add(fr);

        for (Category c : Category.values()) {
            List<Calculator> list = CalculatorRegistry.byCategory(c);
            if (list.isEmpty()) continue;
            out.add(section("cat_" + c.name(), c.title, String.valueOf(list.size()), c));
            for (Calculator calc : list) out.add(calc(calc));
        }
        return out;
    }

    private static Item section(String key, String title, String subtitle, Category seeAll) {
        Item i = new Item(Item.Type.SECTION, "section_" + key);
        i.title = title;
        i.subtitle = subtitle;
        i.seeAll = seeAll;
        return i;
    }

    private static Item calc(Calculator c) {
        Item i = new Item(Item.Type.CALC, "calc_" + c.id());
        i.calc = c;
        return i;
    }

    /** Time-of-day greeting for the home header. */
    public static String greeting(int hourOfDay) {
        if (hourOfDay >= 5 && hourOfDay < 12) return "Good morning";
        if (hourOfDay >= 12 && hourOfDay < 17) return "Good afternoon";
        if (hourOfDay >= 17 && hourOfDay < 22) return "Good evening";
        return "Hello, night owl";
    }

    static boolean equalsNullable(Object a, Object b) { return Objects.equals(a, b); }
}

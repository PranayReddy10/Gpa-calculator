package com.gpa.calculator.data.shop;

import androidx.annotation.Nullable;

import com.gpa.calculator.data.remote.model.ShopCatalog.Category;
import com.gpa.calculator.data.remote.model.ShopCatalog.Product;
import com.gpa.calculator.data.remote.model.ShopCatalog.Variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Search, filter, sort and price formatting for the shop screens (no Android dependencies). */
public final class ShopQuery {
    private ShopQuery() {}

    public enum Sort { FEATURED, PRICE_LOW, PRICE_HIGH, NAME }

    /** Products in {@code categorySlug} (null = all) whose name, category or summary contain every word of {@code query}. */
    public static List<Product> filter(List<Product> all, @Nullable String categorySlug, @Nullable String query) {
        String[] words = query == null ? new String[0] : query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<Product> out = new ArrayList<>();
        for (Product p : all) {
            if (categorySlug != null && !p.category_slugs.contains(categorySlug)) continue;
            if (!matches(p, words)) continue;
            out.add(p);
        }
        return out;
    }

    private static boolean matches(Product p, String[] words) {
        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) return true;
        String hay = (p.name + " " + p.category + " " + p.shortText + " " + String.join(" ", p.category_slugs))
                .toLowerCase(Locale.ROOT);
        for (String w : words) if (!w.isEmpty() && !hay.contains(w)) return false;
        return true;
    }

    /** Returns a sorted copy. FEATURED keeps the shop's own order with featured products first. */
    public static List<Product> sort(List<Product> in, Sort sort) {
        List<Product> out = new ArrayList<>(in);
        switch (sort) {
            case PRICE_LOW: out.sort(Comparator.comparingDouble(p -> p.price)); break;
            case PRICE_HIGH: out.sort((a, b) -> Double.compare(b.max_price > 0 ? b.max_price : b.price, a.max_price > 0 ? a.max_price : a.price)); break;
            case NAME: out.sort((a, b) -> a.name.compareToIgnoreCase(b.name)); break;
            default: {
                List<Product> featured = new ArrayList<>();
                List<Product> rest = new ArrayList<>();
                for (Product p : out) (p.featured ? featured : rest).add(p);
                featured.addAll(rest);
                out = featured;
            }
        }
        return out;
    }

    /** Top-level categories (no parent), most products first; ties keep the shop's order. */
    public static List<Category> topCategories(List<Category> all) {
        List<Category> out = new ArrayList<>();
        for (Category c : all) if (c.parent == null || c.parent.isEmpty()) out.add(c);
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        return out;
    }

    /** "₹1,299" / "₹1,00,000" (Indian digit grouping), "₹99.50" when there are paise; other currencies get their code. */
    public static String money(double amount, @Nullable String currency) {
        long paise = Math.round(Math.abs(amount) * 100);
        String n = indianGroups(paise / 100) + (paise % 100 == 0 ? "" : String.format(Locale.ROOT, ".%02d", paise % 100));
        if (amount < 0) n = "-" + n;
        if (currency == null || currency.isEmpty() || "INR".equalsIgnoreCase(currency)) return "₹" + n;
        return currency.toUpperCase(Locale.ROOT) + " " + n;
    }

    /** 1234567 -> "12,34,567": last three digits, then groups of two. */
    static String indianGroups(long v) {
        String d = Long.toString(v);
        if (d.length() <= 3) return d;
        String out = d.substring(d.length() - 3);
        String head = d.substring(0, d.length() - 3);
        while (!head.isEmpty()) {
            int cut = Math.max(0, head.length() - 2);
            out = head.substring(cut) + "," + out;
            head = head.substring(0, cut);
        }
        return out;
    }

    /** "₹103", or "₹52 – ₹819" when options change the price. */
    public static String priceLabel(Product p) {
        if (p.max_price > p.price + 0.005) return money(p.price, p.currency) + " – " + money(p.max_price, p.currency);
        return money(p.price, p.currency);
    }

    /** The variant whose options equal {@code selected}, or null when the shop did not list one. */
    @Nullable
    public static Variant matchVariant(Product p, Map<String, String> selected) {
        if (selected.isEmpty()) return null;
        for (Variant v : p.variants) {
            if (v.options.size() != selected.size()) continue;
            boolean ok = true;
            for (Map.Entry<String, String> e : selected.entrySet()) {
                String have = v.options.get(e.getKey());
                if (have == null || !have.equalsIgnoreCase(e.getValue())) { ok = false; break; }
            }
            if (ok) return v;
        }
        return null;
    }

    /** Products from the same category, excluding {@code p}, for "You may also like". */
    public static List<Product> related(List<Product> all, Product p, int max) {
        List<Product> out = new ArrayList<>();
        String key = p.category_slugs.isEmpty() ? null : p.category_slugs.get(p.category_slugs.size() - 1);
        for (Product o : all) {
            if (o.id == p.id) continue;
            if (key == null || o.category_slugs.contains(key)) out.add(o);
            if (out.size() >= max) break;
        }
        return Collections.unmodifiableList(out);
    }
}

package com.gpa.calculator.data.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.gpa.calculator.data.remote.model.ShopCatalog.Category;
import com.gpa.calculator.data.remote.model.ShopCatalog.Product;
import com.gpa.calculator.data.remote.model.ShopCatalog.Variant;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopQueryTest {
    private static Product product(int id, String name, double price, double max, boolean featured, String... slugs) {
        Product p = new Product();
        p.id = id; p.name = name; p.price = price; p.max_price = max; p.featured = featured;
        p.category = slugs.length > 0 ? slugs[0] : "";
        p.category_slugs = Arrays.asList(slugs);
        return p;
    }

    private final Product magnet = product(1, "Square Photo Magnet", 103, 103, false, "square-magnets", "magnets");
    private final Product mug = product(2, "Magic Mug", 349, 349, true, "mugs");
    private final Product badge = product(3, "Round Badge", 30, 160, false, "round-badge", "badges");
    private final List<Product> all = Arrays.asList(magnet, mug, badge);

    @Test public void filterByParentCategory() {
        assertEquals(Collections.singletonList(magnet), ShopQuery.filter(all, "magnets", ""));
        assertEquals(3, ShopQuery.filter(all, null, null).size());
    }

    @Test public void searchNeedsEveryWord() {
        assertEquals(Collections.singletonList(magnet), ShopQuery.filter(all, null, "photo MAGNET"));
        assertEquals(0, ShopQuery.filter(all, null, "photo mug").size());
        assertEquals(Collections.singletonList(mug), ShopQuery.filter(all, null, "  mug "));
    }

    @Test public void sortModes() {
        assertEquals(Arrays.asList(mug, magnet, badge), ShopQuery.sort(all, ShopQuery.Sort.FEATURED));
        assertEquals(Arrays.asList(badge, magnet, mug), ShopQuery.sort(all, ShopQuery.Sort.PRICE_LOW));
        // high-to-low uses the top of a price range (badge goes up to 160)
        assertEquals(Arrays.asList(mug, badge, magnet), ShopQuery.sort(all, ShopQuery.Sort.PRICE_HIGH));
        assertEquals(Arrays.asList(mug, badge, magnet), ShopQuery.sort(all, ShopQuery.Sort.NAME));
    }

    @Test public void moneyUsesIndianGrouping() {
        assertEquals("₹103", ShopQuery.money(103, "INR"));
        assertEquals("₹1,00,000", ShopQuery.money(100000, "INR"));
        assertEquals("₹99.50", ShopQuery.money(99.5, ""));
        assertEquals("USD 12", ShopQuery.money(12, "usd"));
        assertEquals("₹12,34,567.05", ShopQuery.money(1234567.05, "INR"));
        assertEquals("₹999", ShopQuery.money(999, "INR"));
        assertEquals("₹1,000", ShopQuery.money(1000, "INR"));
    }

    @Test public void priceLabelShowsRange() {
        assertEquals("₹103", ShopQuery.priceLabel(magnet));
        assertEquals("₹30 – ₹160", ShopQuery.priceLabel(badge));
    }

    @Test public void topCategoriesOnly() {
        Category parent = new Category(); parent.slug = "magnets"; parent.count = 12;
        Category child = new Category(); child.slug = "square-magnets"; child.parent = "magnets"; child.count = 6;
        Category mugs = new Category(); mugs.slug = "mugs"; mugs.count = 4;
        assertEquals(Arrays.asList(parent, mugs), ShopQuery.topCategories(Arrays.asList(mugs, child, parent)));
    }

    @Test public void variantMatching() {
        Variant v = new Variant(); v.options.put("Pack", "Pack-3"); v.price = 154;
        badge.variants = Collections.singletonList(v);
        Map<String, String> sel = new LinkedHashMap<>();
        assertNull(ShopQuery.matchVariant(badge, sel));
        sel.put("Pack", "pack-3");
        assertSame(v, ShopQuery.matchVariant(badge, sel));
        sel.put("Pack", "Pack-5");
        assertNull(ShopQuery.matchVariant(badge, sel));
    }

    @Test public void relatedSharesTopCategory() {
        Product other = product(4, "Round Magnet", 60, 60, false, "round-magnets", "magnets");
        List<Product> list = Arrays.asList(magnet, mug, badge, other);
        assertEquals(Collections.singletonList(other), ShopQuery.related(list, magnet, 6));
    }
}

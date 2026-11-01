package com.gpa.calculator.data.remote.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GET /api/v1/shop: the shop catalogue, already trimmed by the backend. */
public class ShopCatalog {
    public String name = "";
    public String title = "";
    public String subtitle = "";
    public String base_url = "";
    public boolean checkout_in_app = true;
    public long fetched_at;
    public boolean stale;
    public List<Category> categories = new ArrayList<>();
    public List<Product> products = new ArrayList<>();

    public static class Category {
        public int id;
        public String name = "";
        public String slug = "";
        /** Parent category slug, "" for top-level categories. */
        public String parent = "";
        public String image = "";
        public int count;
    }

    public static class Product {
        public int id;
        public String sku = "";
        public String name = "";
        public String slug = "";
        public String url = "";
        public String type = "simple";
        public double price;
        public double regular_price;
        public double max_price;
        public boolean on_sale;
        public int discount_percent;
        public String currency = "INR";
        public boolean in_stock = true;
        public boolean featured;
        public String image = "";
        public String thumbnail = "";
        public List<String> images = new ArrayList<>();
        public String category = "";
        /** The product's categories plus all their parents. */
        public List<String> category_slugs = new ArrayList<>();
        /** Short description; "short" is a Java keyword. */
        @SerializedName("short") public String shortText = "";
        public String description = "";
        /** Option name -> values, e.g. "Pack" -> ["Pack-1", "Pack-2"]. */
        public Map<String, List<String>> attributes = new LinkedHashMap<>();
        public List<Variant> variants = new ArrayList<>();
        public List<Personalisation> personalisation = new ArrayList<>();
        public int production_days;
        public Rating rating = new Rating();
    }

    public static class Variant {
        public int id;
        public Map<String, String> options = new LinkedHashMap<>();
        public double price;
        public double regular_price;
        public boolean in_stock = true;
        public String image = "";
        public String url = "";
    }

    /** What the customer provides on the product page: photos, a choice or some text. */
    public static class Personalisation {
        public String label = "";
        /** "images", "select", "choice", "text" or "textarea". */
        public String type = "";
        public boolean required;
        public String help = "";
        public int max_files;
        public int max_length;
        public List<String> options = new ArrayList<>();
    }

    public static class Rating {
        public double average;
        public int count;
    }
}

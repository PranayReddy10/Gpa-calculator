package com.gpa.calculator.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Currency codes + names supported by the exchange-rate feed (ECB via Frankfurter) plus a few extras. */
public final class Currencies {
    private Currencies() {}

    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    /** Approximate USD-based rates used only until the first live fetch succeeds (keeps the screen usable offline). */
    public static final Map<String, Double> FALLBACK_USD = new LinkedHashMap<>();

    static {
        put("USD", "US Dollar", 1.0);
        put("INR", "Indian Rupee", 83.5);
        put("EUR", "Euro", 0.92);
        put("GBP", "British Pound", 0.78);
        put("AED", "UAE Dirham", 3.67);
        put("SAR", "Saudi Riyal", 3.75);
        put("JPY", "Japanese Yen", 150.0);
        put("CNY", "Chinese Yuan", 7.2);
        put("AUD", "Australian Dollar", 1.52);
        put("CAD", "Canadian Dollar", 1.36);
        put("SGD", "Singapore Dollar", 1.34);
        put("CHF", "Swiss Franc", 0.88);
        put("HKD", "Hong Kong Dollar", 7.8);
        put("NZD", "New Zealand Dollar", 1.64);
        put("SEK", "Swedish Krona", 10.5);
        put("NOK", "Norwegian Krone", 10.7);
        put("DKK", "Danish Krone", 6.9);
        put("PLN", "Polish Zloty", 3.95);
        put("CZK", "Czech Koruna", 23.0);
        put("HUF", "Hungarian Forint", 360.0);
        put("RON", "Romanian Leu", 4.6);
        put("BGN", "Bulgarian Lev", 1.8);
        put("TRY", "Turkish Lira", 32.0);
        put("ZAR", "South African Rand", 18.5);
        put("BRL", "Brazilian Real", 5.1);
        put("MXN", "Mexican Peso", 17.0);
        put("KRW", "South Korean Won", 1350.0);
        put("IDR", "Indonesian Rupiah", 15800.0);
        put("MYR", "Malaysian Ringgit", 4.7);
        put("PHP", "Philippine Peso", 56.5);
        put("THB", "Thai Baht", 36.0);
        put("ILS", "Israeli Shekel", 3.7);
        put("ISK", "Icelandic Krona", 138.0);
    }

    private static void put(String code, String name, double usd) {
        NAMES.put(code, name);
        FALLBACK_USD.put(code, usd);
    }

    public static String label(String code) {
        String n = NAMES.get(code);
        return n == null ? code : code + " · " + n;
    }

    public static String codeFromLabel(String label) {
        int i = label.indexOf(" ·");
        return i > 0 ? label.substring(0, i) : label.trim();
    }

    /** Converts using a rates table where every value is "units per 1 base". */
    public static double convert(double amount, String from, String to, Map<String, Double> rates) {
        Double rf = rates.get(from), rt = rates.get(to);
        if (rf == null || rt == null || rf == 0) throw new CalcException("Rate not available for " + (rf == null ? from : to));
        return amount / rf * rt;
    }
}

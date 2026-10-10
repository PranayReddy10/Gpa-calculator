package com.gpa.calculator.ui.home;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/** Date bucket labels for the history list: Today, Yesterday, Earlier this week, then month + year. */
public final class HistoryGrouping {
    private HistoryGrouping() {}

    public static String label(long ts, long now, ZoneId zone) {
        LocalDate day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (day.equals(today)) return "Today";
        if (day.equals(today.minusDays(1))) return "Yesterday";
        if (day.isAfter(today.minusDays(7)) && !day.isAfter(today)) return "Earlier this week";
        String month = day.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        return month + " " + day.getYear();
    }
}

package com.gpa.calculator.data.remote;

import com.gpa.calculator.data.remote.model.RemoteConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Pure decision logic for the admin pop-up (no Android types, unit tested on the JVM). */
public final class PopupRules {
    private PopupRules() {}

    public static final long NEW_USER_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    /** Snapshot of everything the rules need about this device. */
    public static final class State {
        public long now;
        public ZoneId zone = ZoneId.systemDefault();
        public int versionCode;
        public long firstOpenAt;
        /** When this popup id was last shown on this device, 0 if never. */
        public long lastShownAt;
        /** True once a popup has been shown in the current app session. */
        public boolean shownThisSession;
    }

    public static boolean shouldShow(RemoteConfig.Popup p, State s) {
        if (p == null || !p.enabled || isBlank(p.id)) return false;
        if (isBlank(p.title) && isBlank(p.body) && isBlank(p.image_url)) return false;
        if (s.shownThisSession) return false;
        if (p.min_version_code > 0 && s.versionCode < p.min_version_code) return false;
        if (p.max_version_code > 0 && s.versionCode > p.max_version_code) return false;

        LocalDate today = Instant.ofEpochMilli(s.now).atZone(s.zone).toLocalDate();
        LocalDate start = parse(p.start_date), end = parse(p.end_date);
        if (start != null && today.isBefore(start)) return false;
        if (end != null && today.isAfter(end)) return false;

        boolean isNew = s.now - s.firstOpenAt < NEW_USER_WINDOW_MS;
        if ("new".equals(p.audience) && !isNew) return false;
        if ("returning".equals(p.audience) && isNew) return false;

        switch (p.frequency == null ? "once" : p.frequency) {
            case "every_open":
                return true;
            case "daily":
                return s.lastShownAt == 0 || !Instant.ofEpochMilli(s.lastShownAt).atZone(s.zone).toLocalDate().equals(today);
            default: // once
                return s.lastShownAt == 0;
        }
    }

    private static boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }

    private static LocalDate parse(String d) {
        if (isBlank(d)) return null;
        try {
            return LocalDate.parse(d.trim());
        } catch (DateTimeParseException e) {
            return null; // ignore malformed dates rather than hiding the popup forever
        }
    }
}

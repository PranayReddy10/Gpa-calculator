package com.gpa.calculator.data.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gpa.calculator.data.remote.model.RemoteConfig;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

public class PopupRulesTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static long at(String date) {
        return LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static RemoteConfig.Popup popup() {
        RemoteConfig.Popup p = new RemoteConfig.Popup();
        p.enabled = true;
        p.id = "diwali";
        p.title = "Happy Diwali";
        p.body = "New EMI calculator inside";
        return p;
    }

    private static PopupRules.State state(String today) {
        PopupRules.State s = new PopupRules.State();
        s.zone = ZoneOffset.UTC;
        s.now = at(today);
        s.firstOpenAt = s.now - 30 * DAY;
        s.versionCode = 100;
        return s;
    }

    @Test public void basicOnce() {
        RemoteConfig.Popup p = popup();
        PopupRules.State s = state("2026-10-20");
        assertTrue(PopupRules.shouldShow(p, s));
        s.lastShownAt = s.now - DAY;
        assertFalse(PopupRules.shouldShow(p, s));
    }

    @Test public void disabledOrEmpty() {
        RemoteConfig.Popup p = popup();
        p.enabled = false;
        assertFalse(PopupRules.shouldShow(p, state("2026-10-20")));
        p = popup();
        p.id = "";
        assertFalse(PopupRules.shouldShow(p, state("2026-10-20")));
        p = popup();
        p.title = ""; p.body = ""; p.image_url = "";
        assertFalse(PopupRules.shouldShow(p, state("2026-10-20")));
        assertFalse(PopupRules.shouldShow(null, state("2026-10-20")));
    }

    @Test public void daily() {
        RemoteConfig.Popup p = popup();
        p.frequency = "daily";
        PopupRules.State s = state("2026-10-20");
        s.lastShownAt = at("2026-10-20") - 60_000;
        assertFalse(PopupRules.shouldShow(p, s));
        s.lastShownAt = at("2026-10-19");
        assertTrue(PopupRules.shouldShow(p, s));
    }

    @Test public void everyOpenButOncePerSession() {
        RemoteConfig.Popup p = popup();
        p.frequency = "every_open";
        PopupRules.State s = state("2026-10-20");
        s.lastShownAt = s.now - 1000;
        assertTrue(PopupRules.shouldShow(p, s));
        s.shownThisSession = true;
        assertFalse(PopupRules.shouldShow(p, s));
    }

    @Test public void dateWindow() {
        RemoteConfig.Popup p = popup();
        p.start_date = "2026-10-25";
        p.end_date = "2026-11-05";
        assertFalse(PopupRules.shouldShow(p, state("2026-10-24")));
        assertTrue(PopupRules.shouldShow(p, state("2026-10-25")));
        assertTrue(PopupRules.shouldShow(p, state("2026-11-05")));
        assertFalse(PopupRules.shouldShow(p, state("2026-11-06")));
        p.start_date = "not-a-date";
        p.end_date = "";
        assertTrue(PopupRules.shouldShow(p, state("2026-10-01")));
    }

    @Test public void versionRange() {
        RemoteConfig.Popup p = popup();
        p.min_version_code = 101;
        assertFalse(PopupRules.shouldShow(p, state("2026-10-20")));
        p.min_version_code = 0;
        p.max_version_code = 99;
        assertFalse(PopupRules.shouldShow(p, state("2026-10-20")));
        p.max_version_code = 100;
        assertTrue(PopupRules.shouldShow(p, state("2026-10-20")));
    }

    @Test public void audience() {
        RemoteConfig.Popup p = popup();
        PopupRules.State fresh = state("2026-10-20");
        fresh.firstOpenAt = fresh.now - 2 * DAY;
        PopupRules.State old = state("2026-10-20");
        p.audience = "new";
        assertTrue(PopupRules.shouldShow(p, fresh));
        assertFalse(PopupRules.shouldShow(p, old));
        p.audience = "returning";
        assertFalse(PopupRules.shouldShow(p, fresh));
        assertTrue(PopupRules.shouldShow(p, old));
    }
}

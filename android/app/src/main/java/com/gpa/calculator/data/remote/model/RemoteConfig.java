package com.gpa.calculator.data.remote.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mirrors the JSON returned by backend GET /api/v1/config. Every field has a safe default so the
 * app works offline / before the first fetch. Field names are snake_case to match the API.
 */
public class RemoteConfig {
    public Ads ads = new Ads();
    public App app = new App();
    public Features features = new Features();
    public long fetched_at;

    public static class Ads {
        public boolean enabled = true;
        public boolean test_mode = true;
        /** Order in which networks are tried: "admob", "max", "meta". */
        public List<String> priority = new ArrayList<>(Arrays.asList("admob", "max", "meta"));
        public Network admob = new Network();
        public Network max = new Network();
        public Network meta = new Network();
        public boolean banner_enabled = true;
        public boolean interstitial_enabled = true;
        public boolean app_open_enabled = true;
        public boolean rewarded_enabled = true;
        public boolean native_enabled = true;
        /** Native ad card on every calculator screen. */
        public boolean native_in_calculators = true;
        /** Interstitial triggers (all still respect interstitial_enabled + min interval). */
        public boolean interstitial_after_calc = true;
        public boolean interstitial_on_open = true;
        public int interstitial_open_every_n = 3;
        public boolean interstitial_on_exit = false;
        public int interstitial_every_n_calcs = 3;
        public int interstitial_min_interval_sec = 60;
        public int app_open_min_interval_sec = 240;
        public int first_interstitial_delay_sec = 20;
        public int reward_ad_free_minutes = 120;
        public boolean banner_on_home = true;
        public boolean banner_on_calculator = true;
        public boolean banner_on_result = false;

        public Network network(String name) {
            switch (name) {
                case "admob": return admob;
                case "max": return max;
                case "meta": return meta;
                default: return null;
            }
        }
    }

    public static class Network {
        public boolean enabled = false;
        public String app_id = "";
        public String sdk_key = "";
        public String banner = "";
        public String interstitial = "";
        public String app_open = "";
        public String rewarded = "";
        public String native_ad = "";
    }

    public static class App {
        public int latest_version_code = 0;
        public int force_update_below = 0;
        public String update_message = "A new version is available with new features and fixes.";
        /** Admin-controlled pop-up (see PopupRules for when it shows). */
        public Popup popup = new Popup();
        /** OneSignal app ID (public). Overrides the ONESIGNAL_APP_ID build property when set. */
        public String onesignal_app_id = "";
        public String privacy_url = "";
        public String terms_url = "";
        public String contact_email = "";
        public String share_text = "";
        public String more_apps_url = "";
    }

    public static class Popup {
        public boolean enabled = false;
        /** Change the id to show a new campaign to users who already saw the previous one. */
        public String id = "";
        public String title = "";
        public String body = "";
        public String image_url = "";
        public String primary_label = "OK";
        /** dismiss | url | calculator | rate | share | update | notifications */
        public String primary_action = "dismiss";
        /** URL for "url", calculator id for "calculator". */
        public String primary_value = "";
        public String secondary_label = "";
        /** once | daily | every_open */
        public String frequency = "once";
        /** all | new (installed < 7 days) | returning (installed >= 7 days) */
        public String audience = "all";
        public int delay_sec = 3;
        public int min_version_code = 0;
        public int max_version_code = 0;
        /** Optional yyyy-MM-dd window (inclusive). */
        public String start_date = "";
        public String end_date = "";
    }

    public static class Features {
        public boolean cloud_save = true;
        public boolean analytics = true;
        public boolean rate_prompt = true;
        public int rate_prompt_after_calcs = 8;
        public boolean in_app_update = true;
        /** OneSignal push notifications. */
        public boolean push_enabled = true;
        /** Friendly "turn on notifications?" pop-up before the Android permission dialog. */
        public boolean push_prompt_enabled = true;
        public int push_prompt_after_opens = 2;
        /** OneSignal in-app messages (designed in the OneSignal dashboard). */
        public boolean in_app_messages = true;
        /** Exchange-rate feed (Frankfurter/ECB format: {"base","rates":{...}}); "?from=USD" is appended. */
        public String currency_api_url = "https://api.frankfurter.app/latest";
    }
}

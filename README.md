# GPA Calculator 2.0 — Android app + admin backend

Complete rebuild of the Play Store app `com.gpa.calculator` with a Material 3 UI, animations,
54 calculators, three ad networks controlled from an admin panel, cloud-saved history and
self-hosted usage analytics.

```
android/   Native Android app (Java, Gradle KTS, AGP 8.13, compileSdk 36, minSdk 26)
backend/   PHP 7.4+/8.x + MySQL API and admin panel for shared hosting (cPanel friendly)
```

## What the app includes

**Calculators (54)**

| Category | Calculators |
|---|---|
| Tools | Standard keypad calculator (live result, brackets, %), Currency converter (USD/INR/EUR/GBP/AED and 30+ more, live ECB rates cached for offline, feed URL set in admin panel) |
| Percentage | Marks %, Ratio to %, General % (3 modes), Change in %, Discount (stacked + tax), % Difference, Marks needed |
| Academic | SGPA, SGPA with Grade (4 grade scales), CGPA, CGPA target planner, GPA → %, % → GPA, CGPA → % (CBSE/VTU/JNTU), % → CGPA, GPA scale converter, Final exam grade needed, Attendance (bunk/attend), Grade from marks |
| Finance | EMI with full amortisation schedule + chart, Simple interest, Compound interest, SIP, Lumpsum, FD, RD, Tax/GST (add/remove, CGST/SGST), Tip & split, Profit/Loss, Inflation, ROI/CAGR, Salary breakdown, Loan eligibility, Loan comparison (two offers incl. processing fee) |
| Everyday | Age, Date difference, Days until, BMI, Calories (BMR/TDEE), Fuel cost, Electricity cost, Speed·Distance·Time, Average & statistics, Unit price (best value across up to 3 pack sizes, g/kg/oz/lb/ml/L/fl oz/pcs) |
| Converters | Length, Weight, Area, Volume, Speed, Data size, Time, Temperature |

**UI / animations**: Material 3 with dynamic colour option, light/dark/system theme, AndroidX
splash screen with animated vector logo, staggered card entrance, container-transform shared
element from card to calculator, collapsing large header, spring result-card reveal, count-up
numbers, animated donut and bar charts, heart pop on favourite, swipe-to-delete with undo,
validation shake, haptics, edge-to-edge.

**Home**: time-of-day greeting, hero search card that hides on scroll and returns on scroll up,
pinned category chips, "Continue where you left off" (reopens a calculator with your last
inputs), favourites row, popular row, calculators grouped by category with colour accents.

**History**: grouped by day (Today, Yesterday, Earlier this week, month), swipe to delete
with undo, Clear all (with confirmation; also clears the cloud copy), export as CSV.

**Features**: favourites, history with cloud backup/restore (MySQL), recent inputs per
calculator, copy / share result as image, "Save to CGPA" from the SGPA screens (the CGPA
screen loads your saved semesters), launcher shortcuts (long-press the icon: SGPA, CGPA,
EMI, Calculator, plus recently used), "Add to home screen" for any calculator, in-app review
prompt, in-app update + forced update, rewarded "remove ads for N minutes".

**Push notifications (OneSignal)**: friendly "turn on notifications" pop-up before Android's
permission dialog (after N opens, max 3 times, a week apart), Settings switch, taps deep-link
straight into a calculator or open a link, users tagged with app version / usage for
segments, OneSignal in-app messages on/off from the admin panel. Send pushes from the admin
panel's Notifications tab (segment, one test device, image, schedule, history).

**Pop-up campaigns**: image + title + message + buttons (open link, open calculator, rate,
share, update, ask for notifications). Rules: once / daily / every open, new or returning
users, date window, app version range, delay. One pop-up per session, never over an ad.

**Ads** (all switched from the admin panel): Google AdMob, AppLovin MAX, Meta Audience
Network. AdMob ad units are also filled through AdMob mediation by Meta, Unity Ads and Liftoff
Monetize (Vungle); the adapters are bundled and set up in the AdMob console. Formats: banner, native (an ad card in every calculator screen, compact on the keypad
calculator), interstitial (after N calculations, when opening a calculator every N opens, and
optionally when leaving one), app open and rewarded. Priority/waterfall order, per-network unit
IDs, per-format and per-placement toggles, frequency caps and test mode are all remote. Google
UMP consent is gathered before any request.

**Analytics** (own backend, no third-party SDK): installs, DAU/WAU/MAU, online now, top
calculators, ad impressions by network, versions, countries, device models, 30-day charts.

**Releasing to Google Play:** follow [RELEASE.md](RELEASE.md) (signing, admin settings, Data
safety answers, store listing text). Store graphics are in `docs/store/`.

## Android: build

1. Open `android/` in Android Studio (Ladybug or newer, JDK 17). The Gradle wrapper pins
   Gradle 8.14.3 and AGP 8.13.0.
2. Put your keys in `android/local.properties` (Android Studio already created it with
   `sdk.dir`; it is ignored by git). Copy the lines from `android/local.properties.sample`:
   ```
   API_BASE_URL=https://gpa.jntubooks.in/
   APP_API_KEY=<same value as APP_API_KEY in backend/config.php>
   ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
   APPLOVIN_SDK_KEY=<optional, leave empty to disable MAX>
   ONESIGNAL_APP_ID=<optional, can also be set in the admin panel App tab>
   ```
   Then File > Sync Project with Gradle Files and rebuild. Never edit `BuildConfig.java` under
   `app/build/`: it is regenerated from these values on every build. Without them the app runs
   with Google test ads and the backend disabled.
3. `./gradlew testDebugUnitTest` runs the calculator engine tests (21 tests).
4. `./gradlew assembleRelease` — sign with the **same keystore** as the live app.
   `versionCode` in `app/build.gradle.kts` is 100; raise it above the current Play version.

AdMob app ID and the AppLovin SDK key are compile-time (SDK requirement). Everything else
about ads (unit IDs, on/off, order, frequency) is remote.

## Backend: deploy on shared hosting

1. Upload the **contents** of `backend/` to the document root of `gpa.jntubooks.in` (the folder
   cPanel shows for that subdomain), so `index.php` and `.htaccess` sit at the top level.
2. Create a MySQL database + user in cPanel.
3. Copy `config.sample.php` → `config.php`, fill DB credentials, `APP_API_KEY`,
   `SETUP_KEY`, `ADMIN_USER`, `ADMIN_PASS`.
4. Open `https://gpa.jntubooks.in/setup.php?key=<SETUP_KEY>` once (creates tables and
   the admin user), then **delete `setup.php`**.
5. Admin panel: `https://gpa.jntubooks.in/admin/`. Check that
   `https://gpa.jntubooks.in/config.php` shows **403 Forbidden** (secrets protected).
6. In the Ads tab: enter unit IDs, set priority, turn **Test mode off** when going live.
7. Public pages served by the backend: `/privacy` (edit it in the admin **Privacy** tab) and
   `/app-ads.txt` (built from the AdMob app ID plus extra lines in the **Ads** tab).
8. Push: create a OneSignal app (Android, with your Firebase server key / service account as
   OneSignal asks), put the **App API key** in `ONESIGNAL_API_KEY` in `config.php` and the
   **App ID** in the admin App tab. The PHP `curl` extension must be enabled (it is on almost
   every host).

Requirements: PHP 7.4+ with PDO MySQL, Apache mod_rewrite (`.htaccess` included; if rewrite
is unavailable the app URL can use `index.php?route=/api/v1/config`).

### API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/config` | `X-App-Key` | Ads + app config for the app |
| POST | `/api/v1/device/register` | `X-App-Key` | Upsert device, returns device token |
| POST | `/api/v1/events` | `X-App-Key` | Batch analytics events |
| GET/POST/DELETE | `/api/v1/saves[/{id}]` | device token | Cloud-saved calculations |
| POST | `/api/v1/admin/login` | — | Admin session token |
| GET/PUT | `/api/v1/admin/config` | admin | Read / write remote config |
| GET | `/api/v1/admin/stats` | admin | Dashboard numbers and series |
| GET | `/api/v1/admin/devices`, `/events` | admin | Raw lists |
| GET/POST | `/api/v1/admin/push`, `/admin/push/status` | admin | Send push via OneSignal, history |

Run the backend test locally: `php backend/tests/smoke.php` (uses SQLite, no MySQL needed).

## Notes

- Engine code in `android/app/src/main/java/com/gpa/calculator/core` has no Android
  dependencies; add a calculator by subclassing `SimpleCalc` and registering it in
  `CalculatorRegistry`.
- Event names are plain strings; the dashboard reads `calc_used`, `ad_impression`,
  `app_open` and friends. Raw events are purged after `EVENT_RETENTION_DAYS`.
- Users are anonymous (random device id); no personal data is collected.

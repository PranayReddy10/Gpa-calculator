# Releasing GPA Calculator 2.0 to Google Play

This version replaces the current Play Store app `com.gpa.calculator`. Do the steps in order.

## 1. Before you start: the signing key (most important)

The update **must be signed with the same upload key** as the app already on Play.

- Open Play Console > your app > **Test and release > App integrity**.
- If **Play App Signing** is on, you need your **upload key** keystore (`.jks`). If you lost it, use
  "Request upload key reset" on that page (takes a few days).
- If Play App Signing is off and the original keystore is lost, the app **cannot be updated**.

Never commit the keystore or its passwords.

## 2. Server (https://gpa.jntubooks.in)

1. Upload the latest `backend/` files (keep your existing `config.php`).
2. In `config.php` make sure these are set: database, `APP_API_KEY`, `ONESIGNAL_APP_ID`,
   `ONESIGNAL_API_KEY`. Remove `setup.php` from the server if it is still there.
3. Check in a browser:

| URL | Expected |
|---|---|
| `/api/v1/` | `"ok":true` |
| `/config.php` | 403 Forbidden |
| `/privacy` | Your privacy policy |
| `/app-ads.txt` | A `google.com, pub-…` line |

## 3. Admin panel (https://gpa.jntubooks.in/admin/)

**App tab**
- Contact email and publisher / developer name.
- Leave "Privacy policy URL" empty so the app uses `/privacy`.

**Privacy tab**
- Click **Load default template**, read it, adjust anything that does not match how you run the
  app, then **Save privacy policy**.

**Ads tab**
- AdMob app ID (same as `ADMOB_APP_ID` below) and your real unit IDs: banner, interstitial,
  app open, rewarded, native.
- **Turn Test mode OFF.**
- Ad-free minutes after rewarded video: 5–10.
- Leave "Interstitial when leaving a calculator" off (Google treats exit interstitials as
  disruptive).
- app-ads.txt: add the AppLovin / Meta lines from their dashboards if you use them.

**AdMob website**
- AdMob > Privacy & messaging: publish a **GDPR** message (and **US state** message) so users in
  Europe/UK get the consent form; without it they receive only limited ads.

## 4. Build settings (`android/local.properties`)

```
API_BASE_URL=https://gpa.jntubooks.in/
APP_API_KEY=<same as backend config.php>
# AdMob > Apps > App settings
ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
# optional
APPLOVIN_SDK_KEY=
# optional (the admin panel can set it)
ONESIGNAL_APP_ID=

# must be higher than the version code live on Play
VERSION_CODE=100
VERSION_NAME=2.0.0

RELEASE_STORE_FILE=/path/to/upload-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Find the current version code in Play Console > **App bundle explorer**. Use any higher number.
Put comments on their own line starting with `#`. A `.properties` file has no end-of-line comments.

The release build **refuses to build** if the AdMob ID is the test ID or a placeholder, the API key
is the default, the URL is not https, or the keystore is missing. Fix whatever it lists.

## 5. Build the bundle

Android Studio: **Build > Generate Signed App Bundle / APK > Android App Bundle**, or:

```
cd android
./gradlew clean bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`

## 6. Test before production

1. Play Console > **Test and release > Testing > Internal testing** > create a release, upload the
   `.aab`, add yourself as a tester, install from the Play link.
2. In the app: Settings > long-press the version > **Diagnostics**. Expect "Config: loaded",
   "Ads enabled: true" without "(test mode)", admob "ready".
3. Open a few calculators: banner, native ad and an interstitial (every 3 calculations / opens)
   should be real ads. **Do not click your own ads.**
4. Check: notification prompt, history Clear all, privacy link in Settings, an update from the old
   version keeps working (install the old Play version first, then update through the test track).

## 7. Play Console forms (App content)

**Privacy policy:** `https://gpa.jntubooks.in/privacy`

**Ads:** Yes, the app contains ads.

**App access:** All functionality is available without special access.

**Target audience:** 13 and over (choose "13–15, 16–17, 18+"). Do not include under 13: that
triggers the Families policy, which the ad setup (AppLovin / Meta) is not certified for.

**Content rating:** category Utility / Reference; answer No to all content questions.

**Advertising ID:** Yes, used for Advertising and Analytics.

**Financial features:** the app only has calculators (EMI, loans, interest). Choose
"My app doesn't provide any financial features".

**Health:** BMI and calorie calculators run on the device and no health data is collected. If the
form asks, describe them as fitness calculators.

**Data safety.** These answers match what this app and its SDKs do. Re-check the SDK guides when
filling the form: Google Mobile Ads, AppLovin, Meta Audience Network and OneSignal each publish a
"Data safety" page.

| Question | Answer |
|---|---|
| Collects or shares user data | Yes |
| Encrypted in transit | Yes |
| Users can request deletion | Yes (email; History > Clear all deletes saved calculations) |

| Data type | Collected | Shared | Optional | Purposes |
|---|---|---|---|---|
| Location > Approximate location (from IP, by ad SDKs) | Yes | Yes | No | Advertising, Analytics |
| App activity > App interactions | Yes | Yes | Yes (Settings toggle for our stats) | Analytics, Advertising |
| App activity > Other user-generated content (cloud backup of calculations) | Yes | No | Yes | App functionality |
| App info and performance > Diagnostics | Yes | Yes | No | Analytics, Advertising |
| Device or other IDs (advertising ID, app install ID, push token) | Yes | Yes | No | Advertising, Analytics, App functionality |

No personal info (name, email, phone), no financial info, no photos, no contacts, no precise
location.

## 8. Store listing

**Graphics** (in `docs/store/`):
- App icon: `icon-512.png` (512×512, 32-bit PNG)
- Feature graphic: `feature-graphic-1024x500.png`
- Phone screenshots: take 4–8 from a real phone (Home, SGPA with grade, EMI, Currency, History).
  The images in `docs/screenshots/` are design previews, not real captures, so don't upload them.

**App name** (30 max): `GPA Calculator: CGPA, SGPA & %`

**Short description** (80 max):
`SGPA, CGPA, percentage, EMI and 50+ calculators for students and everyday use`

**Full description:**

```
GPA Calculator is the fastest way to work out your grades — and much more.

STUDENTS
• SGPA with credits and grade points, or with letter grades (O, A+, A, B+ …)
• CGPA from your semesters — save each SGPA and it's added automatically
• CGPA target planner: the SGPA you need to reach your goal
• CGPA ↔ percentage (CBSE, VTU, JNTU and ×10 formulas), GPA scale converter
• Marks %, marks needed, final exam grade needed, attendance (skip or attend)

MONEY
• EMI with full repayment schedule and chart, loan comparison, loan eligibility
• SIP, lumpsum, FD, RD, simple and compound interest, inflation, ROI / CAGR
• GST (add or remove, CGST / SGST), discount, profit / loss, tip and bill split
• Currency converter with live rates for 30+ currencies

EVERYDAY
• Standard calculator, age, date difference, days until
• BMI, calories, fuel cost, electricity cost, speed / distance / time, unit price
• Converters: length, weight, area, volume, speed, data, time, temperature

FEATURES
• Continue where you left off, favourites, history grouped by day
• Cloud backup and restore, export history as CSV
• Share results as an image, dark theme, home-screen shortcuts

Free, no sign-up.
```

**Category:** Education. **Website:** `https://gpa.jntubooks.in` (AdMob reads `app-ads.txt` from
here; if AdMob reports it missing after a few days, also place the same file at
`https://jntubooks.in/app-ads.txt`).

**Release notes:**

```
All-new GPA Calculator 2.0
• Fresh design with dark theme
• 54 calculators: SGPA, CGPA target planner, EMI schedule, loan comparison, currency and more
• Save semesters to your CGPA, continue where you left off, favourites
• History grouped by day, cloud backup and CSV export
```

## 9. Release

1. **Production > Create new release**, upload the same `.aab`, paste the release notes.
2. Use a **staged rollout** (start at 10–20%), watch Android vitals and the admin dashboard for a
   day or two, then increase to 100%.
3. In AdMob, link the app to its Play listing once the update is live.

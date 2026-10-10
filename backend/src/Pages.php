<?php
declare(strict_types=1);

namespace Gpa;

/** Public pages served by the backend: the privacy policy and app-ads.txt. */
final class Pages
{
    public static function privacy(): Response
    {
        $cfg = AppConfig::load();
        $md = trim($cfg['app']['privacy_policy']) !== '' ? $cfg['app']['privacy_policy'] : self::defaultPrivacyMarkdown($cfg);
        $updated = $cfg['app']['privacy_updated'] !== '' ? $cfg['app']['privacy_updated'] : gmdate('Y-m-d', $cfg['updated_at'] ?: time());
        $body = self::markdown(str_replace('{{updated}}', $updated, $md));
        $html = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            . '<title>Privacy Policy · GPA Calculator</title><style>'
            . ':root{--bg:#fff;--fg:#1d1d1f;--muted:#5f6368;--line:#e5e7eb;--brand:#c8102e}'
            . '@media (prefers-color-scheme:dark){:root{--bg:#141414;--fg:#ececec;--muted:#a0a0a0;--line:#2c2c2c;--brand:#ff8a80}}'
            . 'body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}'
            . 'main{max-width:760px;margin:0 auto;padding:32px 20px 64px}h1{font-size:28px;margin:0 0 8px}h2{font-size:20px;margin:32px 0 8px;padding-top:16px;border-top:1px solid var(--line)}'
            . 'h3{font-size:17px;margin:20px 0 6px}p,li{color:var(--fg)}ul{padding-left:22px}a{color:var(--brand)}.muted{color:var(--muted);font-size:14px}'
            . '</style></head><body><main>' . $body . '</main></body></html>';
        return Response::raw(200, 'text/html; charset=utf-8', $html);
    }

    public static function appAdsTxt(): Response
    {
        $cfg = AppConfig::load();
        $lines = [];
        if (preg_match('/^ca-app-pub-(\d{16})~\d+$/', $cfg['ads']['admob']['app_id'], $m)) {
            $lines[] = 'google.com, pub-' . $m[1] . ', DIRECT, f08c47fec0942fa0';
        }
        foreach (preg_split('/\r\n|\r|\n/', $cfg['ads']['app_ads_txt']) as $l) {
            $l = trim($l);
            if ($l !== '' && !in_array($l, $lines, true)) $lines[] = $l;
        }
        if (!$lines) $lines[] = '# Add your AdMob app ID in the admin panel (Ads tab) to publish your app-ads.txt line.';
        return Response::raw(200, 'text/plain; charset=utf-8', implode("\n", $lines) . "\n");
    }

    /** Accurate default policy for this app; admins can load it into the editor and adapt it. */
    public static function defaultPrivacyMarkdown(array $cfg): string
    {
        $publisher = trim($cfg['app']['publisher_name']) !== '' ? trim($cfg['app']['publisher_name']) : 'the developer of GPA Calculator';
        $email = trim($cfg['app']['contact_email']);
        $contact = $email !== '' ? '[' . $email . '](mailto:' . $email . ')' : 'the contact email shown on our Google Play listing';
        $days = (int)Db::config('EVENT_RETENTION_DAYS', 90);
        return <<<MD
# Privacy Policy

Effective date: {{updated}}

GPA Calculator ("the app") is published by {$publisher}. This policy explains what information the app collects, how it is used and the choices you have.

## Summary

- There is no account or sign-up. The app never asks for your name, email address or phone number.
- Calculations run on your phone. If cloud backup is on, saved calculations are stored on our server, linked only to a random app ID.
- The app is free and shows ads from Google AdMob, AppLovin and Meta Audience Network, which may use your device's advertising ID.

## Information we collect

### Information you enter

Numbers you type into calculators are processed on your device and saved in your calculation history on your device. When **Back up history to cloud** is on (Settings), saved calculations (calculator name, inputs and results) are sent to our server so you can restore them later.

### Device and usage information

To run the app and understand how it is used, the app sends our server:

- a random installation ID created by the app (it is not your advertising ID and does not identify you);
- device manufacturer and model, Android version, app version, language, country and time-zone settings;
- the IP address of each request;
- usage events such as which calculators are opened and used, ad impressions and notification opens.

You can stop usage statistics at any time in **Settings > Share anonymous usage statistics**.

### Advertising

Ads are provided by Google AdMob, AppLovin and Meta Audience Network. These partners may collect and use your device's advertising ID, IP address, device information and your interactions with ads to show, measure and personalise ads and to prevent fraud. See their policies:

- Google: https://policies.google.com/technologies/partner-sites
- AppLovin: https://www.applovin.com/privacy/
- Meta: https://www.facebook.com/privacy/policy/

Where the law requires consent (for example in the EEA and the UK), the app asks for it using Google's consent tool, and you can change your choice at any time in **Settings > Privacy options**. You can reset or delete your advertising ID in your phone's settings (**Settings > Privacy > Ads**).

### Notifications

If you allow notifications, OneSignal provides the push service. It receives a push token, basic device information and the app's installation ID so that notifications reach your device. You can turn notifications off in **Settings > Notifications** or in your phone's settings. OneSignal's policy: https://onesignal.com/privacy_policy

### Exchange rates

The currency converter downloads public exchange rates from an exchange-rate service. That request contains no personal information, but like any internet request it reveals your IP address to that service.

## How we use information

- To provide features you turn on, such as cloud backup, restore and notifications.
- To understand how the app is used and improve it, using aggregate statistics.
- To show ads that keep the app free.

We do not sell your personal information and we do not use it to identify you.

## Sharing

We share information only with the service providers named above (ad networks, OneSignal and our hosting provider) so they can operate these services for the app, or when the law requires it.

## Data retention

- Usage events are deleted automatically after {$days} days.
- Saved calculations stay on our server until you delete them.
- The installation record (device and app details) is kept while the app is in use and deleted on request.

## Your choices and deleting your data

- Turn cloud backup, usage statistics and notifications on or off in Settings.
- **History > Clear all** deletes your saved calculations from your phone and from our server.
- To delete everything linked to your installation, email {$contact} with the device ID shown in **Settings > Diagnostics** (long-press the version number). Uninstalling the app removes the data stored on your phone.

## Children

The app is not directed to children under 13, and we do not knowingly collect personal information from children under 13. If you believe a child has provided information, contact us and we will delete it.

## Security

Information sent between the app and our server is encrypted with HTTPS.

## Changes to this policy

We may update this policy from time to time. The effective date at the top shows when it last changed.

## Contact

{$publisher} — {$contact}
MD;
    }

    /** Small, safe Markdown subset: #/##/### headings, - lists, paragraphs, **bold**, [text](url), bare URLs. */
    public static function markdown(string $md): string
    {
        $out = [];
        $para = [];
        $list = [];
        $flushPara = function () use (&$para, &$out) {
            if ($para) { $out[] = '<p>' . self::inline(implode(' ', $para)) . '</p>'; $para = []; }
        };
        $flushList = function () use (&$list, &$out) {
            if ($list) { $out[] = '<ul>' . implode('', array_map(fn($i) => '<li>' . self::inline($i) . '</li>', $list)) . '</ul>'; $list = []; }
        };
        foreach (preg_split('/\r\n|\r|\n/', $md) as $line) {
            $t = trim($line);
            if ($t === '') { $flushPara(); $flushList(); continue; }
            if (preg_match('/^(#{1,3})\s+(.+)$/', $t, $m)) {
                $flushPara(); $flushList();
                $lvl = strlen($m[1]);
                $out[] = "<h$lvl>" . self::inline($m[2]) . "</h$lvl>";
                continue;
            }
            if (preg_match('/^[-*]\s+(.+)$/', $t, $m)) { $flushPara(); $list[] = $m[1]; continue; }
            $flushList();
            $para[] = $t;
        }
        $flushPara(); $flushList();
        return implode("\n", $out);
    }

    private static function inline(string $s): string
    {
        $s = htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
        $links = [];
        // [text](url) — only http(s) and mailto
        $s = preg_replace_callback('/\[([^\]]+)\]\(((?:https?:\/\/|mailto:)[^\s)]+)\)/', function ($m) use (&$links) {
            $links[] = '<a href="' . $m[2] . '" rel="noopener nofollow">' . $m[1] . '</a>';
            return "\x01" . (count($links) - 1) . "\x01";
        }, $s);
        // bare URLs
        $s = preg_replace_callback('#\bhttps?://[^\s<]+[^\s<.,;:!?)]#', function ($m) use (&$links) {
            $links[] = '<a href="' . $m[0] . '" rel="noopener nofollow">' . $m[0] . '</a>';
            return "\x01" . (count($links) - 1) . "\x01";
        }, $s);
        $s = preg_replace('/\*\*(.+?)\*\*/', '<strong>$1</strong>', $s);
        return preg_replace_callback("/\x01(\d+)\x01/", fn($m) => $links[(int)$m[1]], $s);
    }
}

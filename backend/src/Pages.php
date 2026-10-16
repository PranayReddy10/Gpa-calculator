<?php
declare(strict_types=1);

namespace Gpa;

/** Public pages served by the backend: landing page, privacy policy and app-ads.txt. */
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

    public const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.gpa.calculator';

    /** Public landing page at the site root (the API status JSON stays at /api/v1). */
    public static function landing(): Response
    {
        $cfg = AppConfig::load();
        $e = static fn(string $v): string => htmlspecialchars($v, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $publisher = trim($cfg['app']['publisher_name']) !== '' ? trim($cfg['app']['publisher_name']) : 'GPA Calculator';
        $email = trim($cfg['app']['contact_email']);
        $privacy = trim($cfg['app']['privacy_url']) !== '' ? trim($cfg['app']['privacy_url']) : 'privacy';
        $play = self::PLAY_URL;
        $year = gmdate('Y');

        $features = [
            ['🎓', 'SGPA & CGPA', 'Grade-wise SGPA, CGPA across semesters, a CGPA target planner and 10, 5 and 4-point scale conversion.'],
            ['%', 'Marks & percentage', 'Marks %, percentage change, discount, ratio and “marks needed” for the score you want.'],
            ['₹', 'EMI & loans', 'EMI with a full repayment schedule, loan comparison, eligibility, SIP, FD, RD and GST.'],
            ['⇄', 'Currency converter', 'Convert between 30+ currencies with regularly refreshed rates.'],
            ['⚡', 'Everyday tools', 'A smart calculator, age, date difference, BMI, unit price, fuel and electricity cost.'],
            ['★', 'History & favourites', 'Every result is saved by day. Pin favourites, share results or export them.'],
        ];
        $cards = '';
        foreach ($features as [$icon, $title, $text]) {
            $cards .= '<article class="card"><div class="ic" aria-hidden="true">' . $e($icon) . '</div><h3>' . $e($title) . '</h3><p>' . $e($text) . '</p></article>';
        }
        $contact = $email !== '' ? '<a href="mailto:' . $e($email) . '">' . $e($email) . '</a>' : '';

        $css = <<<'CSS'
:root{--bg:#fff8f7;--surface:#ffffff;--fg:#201a1a;--muted:#6b5f5f;--line:#f0dcda;--brand:#c8102e;--brand2:#b0125c;--chip:#ffe9e7}
@media (prefers-color-scheme:dark){:root{--bg:#141112;--surface:#1f1a1b;--fg:#f1e9e8;--muted:#b3a5a4;--line:#3a2f30;--brand:#ff8a80;--brand2:#ff6f9a;--chip:#3a2427}}
*{box-sizing:border-box}html{scroll-behavior:smooth}
body{margin:0;background:var(--bg);color:var(--fg);font:17px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
a{color:inherit}
.wrap{max-width:1120px;margin:0 auto;padding:0 20px}
header.top{position:absolute;inset:0 0 auto 0;z-index:5}
header.top .wrap{display:flex;align-items:center;justify-content:space-between;height:72px}
.brand{display:flex;align-items:center;gap:10px;color:#fff;text-decoration:none;font-weight:700;font-size:18px}
.brand img{width:36px;height:36px;border-radius:10px;box-shadow:0 4px 12px rgba(0,0,0,.2)}
nav a{color:#fff;text-decoration:none;opacity:.9;margin-left:22px;font-size:15px;font-weight:500}nav a:hover{opacity:1;text-decoration:underline}
.hero{position:relative;overflow:hidden;color:#fff;background:linear-gradient(150deg,#ef4444 0%,#d3123a 52%,#b0125c 100%);padding:120px 0 0}
.hero:before,.hero:after{content:"";position:absolute;border-radius:50%;background:rgba(255,255,255,.08)}
.hero:before{width:620px;height:620px;right:-180px;top:-240px}.hero:after{width:420px;height:420px;left:-160px;bottom:-120px;background:rgba(255,255,255,.06)}
.hero .wrap{position:relative;z-index:1;display:grid;grid-template-columns:1.05fr 1fr;gap:40px;align-items:center}
.hero h1{font-size:clamp(36px,5.4vw,60px);line-height:1.05;letter-spacing:-1.5px;margin:0 0 18px}
.hero p.lead{font-size:clamp(17px,2vw,20px);opacity:.95;margin:0 0 28px;max-width:520px}
.pill{display:inline-block;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.35);border-radius:999px;padding:6px 14px;font-size:14px;font-weight:600;margin-bottom:18px}
.cta{display:flex;flex-wrap:wrap;gap:12px;align-items:center}
.btn{display:inline-flex;align-items:center;gap:10px;text-decoration:none;font-weight:700;border-radius:14px;padding:14px 22px;font-size:16px;transition:transform .15s ease,box-shadow .15s ease}
.btn:hover{transform:translateY(-2px)}
.btn.play{background:#111;color:#fff;box-shadow:0 10px 24px rgba(40,0,10,.35)}
.btn.play small{display:block;font-size:11px;font-weight:500;opacity:.8;line-height:1.1}.btn.play span{line-height:1.15}
.btn.ghost{color:#fff;border:1.5px solid rgba(255,255,255,.6)}
.stats{display:flex;gap:28px;margin-top:32px;padding-bottom:40px}.stats b{display:block;font-size:26px}.stats span{font-size:14px;opacity:.85}
.phones{position:relative;height:600px}
.phone{position:absolute;width:250px;border-radius:38px;padding:9px;background:#1d1b1e;box-shadow:0 30px 60px rgba(50,0,20,.45),inset 0 0 0 2px #3a3639}
.phone img{display:block;width:100%;height:auto;border-radius:30px}
.phone.p1{left:50%;top:20px;transform:translateX(-50%);z-index:3}
.phone.p2{left:2%;top:90px;transform:rotate(-8deg) scale(.88);z-index:2;opacity:.97}
.phone.p3{right:2%;top:90px;transform:rotate(8deg) scale(.88);z-index:2;opacity:.97}
section{padding:80px 0}
.sec-h{text-align:center;max-width:820px;margin:0 auto 44px}
.sec-h h2{font-size:clamp(28px,3.6vw,40px);letter-spacing:-.8px;margin:0 0 10px}.sec-h p{color:var(--muted);margin:0}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}
.card{background:var(--surface);border:1px solid var(--line);border-radius:22px;padding:24px;transition:transform .2s ease,box-shadow .2s ease}
.card:hover{transform:translateY(-4px);box-shadow:0 16px 36px rgba(120,20,40,.12)}
.card .ic{width:48px;height:48px;border-radius:14px;display:grid;place-items:center;font-size:22px;font-weight:800;color:#fff;background:linear-gradient(135deg,#ef4444,#b0125c);margin-bottom:14px}
.card h3{margin:0 0 6px;font-size:19px}.card p{margin:0;color:var(--muted);font-size:15.5px}
.cats{display:flex;flex-wrap:wrap;justify-content:center;gap:10px;margin-top:34px}
.cats span{background:var(--chip);color:var(--fg);border-radius:999px;padding:8px 16px;font-size:14.5px;font-weight:600}
.trust{background:var(--surface);border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
.trust .wrap{display:grid;grid-template-columns:repeat(3,1fr);gap:24px}
.trust h3{margin:0 0 6px;font-size:18px}.trust p{margin:0;color:var(--muted);font-size:15.5px}
.trust a{color:var(--brand);font-weight:600}
.final{text-align:center}
.final .box{background:linear-gradient(150deg,#ef4444,#d3123a 55%,#b0125c);color:#fff;border-radius:28px;padding:56px 24px}
.final h2{margin:0 0 10px;font-size:clamp(26px,3.4vw,38px);letter-spacing:-.6px}.final p{margin:0 0 26px;opacity:.95}
footer{padding:28px 0 40px;color:var(--muted);font-size:14.5px}
footer .wrap{display:flex;flex-wrap:wrap;gap:12px 24px;justify-content:space-between;align-items:center}
footer a{color:var(--muted)}footer nav a{color:var(--muted);margin:0 0 0 18px}
.js .reveal{opacity:0;transform:translateY(18px);transition:opacity .6s ease,transform .6s ease}.js .reveal.in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){.js .reveal{opacity:1;transform:none;transition:none}.btn,.card{transition:none}}
@media (max-width:900px){.hero .wrap{grid-template-columns:1fr;text-align:center}.hero p.lead{margin-left:auto;margin-right:auto}.cta,.stats{justify-content:center}
 .phones{height:470px}.phone{width:210px}.grid{grid-template-columns:repeat(2,1fr)}.trust .wrap{grid-template-columns:1fr}nav a.hide-sm{display:none}}
@media (max-width:560px){.grid{grid-template-columns:1fr}.phone.p2,.phone.p3{display:none}.phones{height:440px}.stats{gap:20px}section{padding:60px 0}}
CSS;

        $playIcon = '<svg width="22" height="24" viewBox="0 0 22 24" aria-hidden="true"><path fill="#00d7fe" d="M1 1.2 12.4 12 1 22.8c-.4-.3-.6-.8-.6-1.4V2.6c0-.6.2-1.1.6-1.4z"/><path fill="#ffce00" d="m16.2 15.8-3.8-3.8 3.8-3.8 4.3 2.4c1.3.7 1.3 2.1 0 2.8z"/><path fill="#ff3a44" d="M16.2 15.8 12.4 12 1 22.8c.4.4 1.1.5 1.8.1z"/><path fill="#00f076" d="M16.2 8.2 2.8 1.1C2.1.7 1.4.8 1 1.2L12.4 12z"/></svg>';

        $html = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            . '<title>GPA Calculator – SGPA, CGPA, Percentage, EMI &amp; more</title>'
            . '<meta name="description" content="Free GPA Calculator app: SGPA, CGPA, marks percentage, EMI, currency converter and 50+ calculators for students and everyday life.">'
            . '<meta name="theme-color" content="#d3123a">'
            . '<meta property="og:title" content="GPA Calculator"><meta property="og:description" content="SGPA, CGPA, percentage, EMI and 50+ calculators in one free app.">'
            . '<meta property="og:type" content="website"><meta property="og:image" content="assets/og.jpg">'
            . '<link rel="icon" type="image/png" href="assets/favicon-32.png"><link rel="apple-touch-icon" href="assets/icon-192.png">'
            . '<script>document.documentElement.className+=" js"</script>'
            . '<style>' . $css . '</style></head><body>'
            . '<header class="top"><div class="wrap"><a class="brand" href="./"><img src="assets/icon-192.png" alt="" width="36" height="36">GPA Calculator</a>'
            . '<nav><a class="hide-sm" href="#features">Features</a><a class="hide-sm" href="' . $e($privacy) . '">Privacy</a><a href="' . $e($play) . '" rel="noopener">Get the app</a></nav></div></header>'
            . '<main><section class="hero" style="padding-bottom:0"><div class="wrap"><div>'
            . '<span class="pill">Free · No sign-up · Works offline</span>'
            . '<h1>Every calculator a student needs.</h1>'
            . '<p class="lead">SGPA, CGPA, marks percentage, EMI, currency and 50+ more calculators in one fast, beautiful app.</p>'
            . '<div class="cta"><a class="btn play" href="' . $e($play) . '" rel="noopener">' . $playIcon . '<span><small>GET IT ON</small>Google Play</span></a>'
            . '<a class="btn ghost" href="#features">See features</a></div>'
            . '<div class="stats"><div><b>50+</b><span>calculators</span></div><div><b>10 / 5 / 4</b><span>point GPA scales</span></div><div><b>30+</b><span>currencies</span></div></div>'
            . '</div><div class="phones" aria-hidden="true">'
            . '<div class="phone p2"><img src="assets/screen-sgpa.jpg" alt="" width="540" height="1199" loading="lazy"></div>'
            . '<div class="phone p3"><img src="assets/screen-emi.jpg" alt="" width="540" height="1199" loading="lazy"></div>'
            . '<div class="phone p1"><img src="assets/screen-home.jpg" alt="GPA Calculator home screen" width="540" height="1199"></div>'
            . '</div></div></section>'
            . '<section id="features"><div class="wrap"><div class="sec-h reveal"><h2>Built for exams, fees and everyday maths</h2>'
            . '<p>Clear inputs, instant results and nothing in the way.</p></div>'
            . '<div class="grid">' . $cards . '</div>'
            . '<div class="cats reveal"><span>Academic</span><span>Percentage</span><span>Finance</span><span>Everyday</span><span>Converters</span></div></div></section>'
            . '<section class="trust"><div class="wrap">'
            . '<div class="reveal"><h3>No account needed</h3><p>Open the app and start calculating. We never ask for your name, email or phone number.</p></div>'
            . '<div class="reveal"><h3>Your numbers stay on your phone</h3><p>Calculations run on your device. Cloud backup of history is optional and can be turned off.</p></div>'
            . '<div class="reveal"><h3>Clear about privacy</h3><p>See exactly what is collected and why in our <a href="' . $e($privacy) . '">privacy policy</a>.</p></div>'
            . '</div></section>'
            . '<section class="final"><div class="wrap"><div class="box reveal"><h2>Calculate smarter, today.</h2><p>Free on Google Play for Android phones.</p>'
            . '<a class="btn play" href="' . $e($play) . '" rel="noopener">' . $playIcon . '<span><small>GET IT ON</small>Google Play</span></a></div></div></section></main>'
            . '<footer><div class="wrap"><div>© ' . $year . ' ' . $e($publisher) . '</div>'
            . '<nav><a href="' . $e($privacy) . '">Privacy policy</a>' . ($contact !== '' ? ' ' . str_replace('<a ', '<a style="margin-left:18px" ', $contact) : '') . '</nav></div></footer>'
            . '<script>(function(){var els=document.querySelectorAll(".reveal,.card");if(!("IntersectionObserver" in window)){els.forEach(function(e){e.classList.add("in")});return}'
            . 'els.forEach(function(e){e.classList.add("reveal")});var io=new IntersectionObserver(function(es){es.forEach(function(x){if(x.isIntersecting){x.target.classList.add("in");io.unobserve(x.target)}})},{threshold:.12});'
            . 'els.forEach(function(e){io.observe(e)})})();</script>'
            . '</body></html>';
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
- The app is free and shows ads from Google AdMob and its partners (Meta Audience Network, Unity Ads, Liftoff Monetize), and from AppLovin, which may use your device's advertising ID.

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

Ads are provided by Google AdMob and the ad networks it works with for this app (Meta Audience Network, Unity Ads and Liftoff Monetize, formerly Vungle), and by AppLovin. These partners may collect and use your device's advertising ID, IP address, device information and your interactions with ads to show, measure and personalise ads and to prevent fraud. See their policies:

- Google: https://policies.google.com/technologies/partner-sites
- AppLovin: https://www.applovin.com/privacy/
- Meta: https://www.facebook.com/privacy/policy/
- Unity Ads: https://unity.com/legal/game-player-and-app-user-privacy-policy
- Liftoff Monetize (Vungle): https://liftoff.io/privacy-policy/

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

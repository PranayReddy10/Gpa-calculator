<?php
declare(strict_types=1);

namespace Gpa;

/** Sends push notifications through the OneSignal REST API and keeps a history log. */
final class Push
{
    public const ENDPOINT = 'https://api.onesignal.com/notifications?c=push';
    public const SEGMENTS = ['Total Subscriptions', 'Active Subscriptions', 'Engaged Subscriptions', 'Inactive Subscriptions'];

    /** @var callable|null fn(string $url, array $headers, string $body): array{0:int,1:string} — overridden in tests */
    public static $transport = null;

    public static function appId(): string
    {
        $id = (string)Db::config('ONESIGNAL_APP_ID', '');
        if ($id === '') $id = (string)(AppConfig::load()['app']['onesignal_app_id'] ?? '');
        return $id;
    }

    public static function isConfigured(): bool
    {
        return self::appId() !== '' && (string)Db::config('ONESIGNAL_API_KEY', '') !== '';
    }

    /** Validates admin input and builds the OneSignal request body. */
    public static function buildPayload(array $in, string $appId): array
    {
        $title = trim((string)($in['title'] ?? ''));
        $message = trim((string)($in['message'] ?? ''));
        if ($message === '') throw new HttpException(422, 'Message is required');
        if (mb_strlen($title) > 100) throw new HttpException(422, 'Title is too long (max 100)');
        if (mb_strlen($message) > 500) throw new HttpException(422, 'Message is too long (max 500)');

        $p = [
            'app_id' => $appId,
            'target_channel' => 'push',
            'contents' => ['en' => $message],
            'priority' => 10,
        ];
        if ($title !== '') $p['headings'] = ['en' => $title];

        $device = trim((string)($in['test_device_id'] ?? ''));
        if ($device !== '') {
            if (!preg_match('/^[A-Za-z0-9\-_]{1,64}$/', $device)) throw new HttpException(422, 'Invalid device id');
            $p['include_aliases'] = ['external_id' => [$device]];
        } else {
            $segment = trim((string)($in['segment'] ?? 'Total Subscriptions'));
            if ($segment === '' || mb_strlen($segment) > 100) throw new HttpException(422, 'Choose a segment');
            $p['included_segments'] = [$segment];
        }

        // Taps are routed by the app (PushManager): calculator deep link or a URL opened by the app.
        $data = [];
        $calc = trim((string)($in['calc'] ?? ''));
        if ($calc !== '') {
            if (!preg_match('/^[a-z0-9_]{1,64}$/', $calc)) throw new HttpException(422, 'Calculator id must be lowercase letters, digits and _');
            $data['calc'] = $calc;
        }
        $url = trim((string)($in['url'] ?? ''));
        if ($url !== '') {
            if (!preg_match('#^https?://#i', $url)) throw new HttpException(422, 'Link must start with http:// or https://');
            $data['open_url'] = $url;
        }
        if ($data) $p['data'] = $data;

        $image = trim((string)($in['image'] ?? ''));
        if ($image !== '') {
            if (!preg_match('#^https://#i', $image)) throw new HttpException(422, 'Image URL must start with https://');
            $p['big_picture'] = $image;
        }

        $sendAfter = trim((string)($in['send_after'] ?? ''));
        if ($sendAfter !== '') {
            $ts = strtotime($sendAfter);
            if ($ts === false) throw new HttpException(422, 'Invalid schedule time');
            if ($ts < time() - 60) throw new HttpException(422, 'Schedule time is in the past');
            $p['send_after'] = gmdate('Y-m-d H:i:s', $ts) . ' GMT+0000';
        }
        $name = trim((string)($in['name'] ?? ''));
        if ($name !== '') $p['name'] = mb_substr($name, 0, 100);
        return $p;
    }

    /** @return array the push_log row */
    public static function send(array $in, string $admin): array
    {
        self::ensureTable();
        $apiKey = (string)Db::config('ONESIGNAL_API_KEY', '');
        $appId = self::appId();
        if ($apiKey === '' || $appId === '') {
            throw new HttpException(422, 'OneSignal is not configured: set ONESIGNAL_API_KEY in config.php and the app ID in config.php or the App tab');
        }
        $payload = self::buildPayload($in, $appId);
        $target = isset($payload['include_aliases']) ? 'device:' . $payload['include_aliases']['external_id'][0] : 'segment:' . $payload['included_segments'][0];
        // New "os_v2_" App API keys use "Key", legacy REST keys use "Basic".
        $scheme = strpos($apiKey, 'os_v2_') === 0 ? 'Key' : 'Basic';
        $headers = ['Content-Type: application/json; charset=utf-8', 'Accept: application/json', 'Authorization: ' . $scheme . ' ' . $apiKey];
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

        [$status, $resp] = (self::$transport ?? [self::class, 'curl'])(self::ENDPOINT, $headers, $body);
        $json = json_decode((string)$resp, true);
        $id = is_array($json) ? (string)($json['id'] ?? '') : '';
        $errors = is_array($json) && !empty($json['errors']) ? $json['errors'] : null;
        $ok = $status >= 200 && $status < 300 && $id !== '' && !$errors;
        $error = null;
        if (!$ok) {
            $error = $errors ? (is_array($errors) ? implode('; ', array_map(fn($e) => is_string($e) ? $e : json_encode($e), (array)$errors)) : (string)$errors)
                : ($status === 0 ? 'Could not reach OneSignal: ' . $resp : 'OneSignal HTTP ' . $status);
        }
        $rowId = Db::insert('INSERT INTO push_log (title, message, target, payload, onesignal_id, recipients, status, error, admin, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', [
            $payload['headings']['en'] ?? null, $payload['contents']['en'], $target, $body,
            $id !== '' ? $id : null, isset($json['recipients']) ? (int)$json['recipients'] : null,
            $ok ? (isset($payload['send_after']) ? 'scheduled' : 'sent') : 'failed', $error, $admin, time(),
        ]);
        $row = Db::row('SELECT * FROM push_log WHERE id = ?', [$rowId]);
        if (!$ok) throw new HttpException(502, 'OneSignal rejected the notification: ' . $error);
        return $row;
    }

    public static function history(int $limit = 50): array
    {
        self::ensureTable();
        return Db::rows('SELECT id, title, message, target, onesignal_id, recipients, status, error, admin, created_at FROM push_log ORDER BY id DESC LIMIT ' . max(1, min(200, $limit)));
    }

    /** Older installs ran setup.php before push_log existed; create it on first use. */
    public static function ensureTable(): void
    {
        static $done = false;
        if ($done) return;
        $sql = file_get_contents(__DIR__ . '/../' . (Db::isSqlite() ? 'schema.sqlite.sql' : 'schema.sql'));
        if (preg_match('/CREATE TABLE IF NOT EXISTS push_log.*?;/s', $sql, $m)) {
            Db::pdo()->exec(rtrim($m[0], ';'));
            if (!Db::isSqlite()) {
                // MySQL DDL already includes the index; nothing else to do
            }
        }
        $done = true;
    }

    /** @return array{0:int,1:string} */
    public static function curl(string $url, array $headers, string $body): array
    {
        if (!function_exists('curl_init')) return [0, 'PHP curl extension is not enabled'];
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_TIMEOUT => 20,
        ]);
        $resp = curl_exec($ch);
        $status = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        return [$resp === false ? 0 : $status, $resp === false ? $err : (string)$resp];
    }
}

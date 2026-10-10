<?php
declare(strict_types=1);

namespace Gpa;

/**
 * The remote config consumed by the Android app (ads + app flags). Stored as one JSON document.
 * defaults() is the contract with RemoteConfig.java: keep field names in sync.
 */
final class AppConfig
{
    public static function defaults(): array
    {
        return [
            'ads' => [
                'enabled' => true,
                'test_mode' => true,
                'priority' => ['admob', 'max', 'meta'],
                'admob' => ['enabled' => true, 'app_id' => '', 'banner' => '', 'interstitial' => '', 'app_open' => '', 'rewarded' => '', 'native_ad' => ''],
                'max' => ['enabled' => false, 'sdk_key' => '', 'banner' => '', 'interstitial' => '', 'app_open' => '', 'rewarded' => '', 'native_ad' => ''],
                'meta' => ['enabled' => false, 'app_id' => '', 'banner' => '', 'interstitial' => '', 'app_open' => '', 'rewarded' => '', 'native_ad' => ''],
                'banner_enabled' => true,
                'interstitial_enabled' => true,
                'app_open_enabled' => true,
                'rewarded_enabled' => true,
                'native_enabled' => true,
                'native_in_calculators' => true,
                'interstitial_after_calc' => true,
                'interstitial_on_open' => true,
                'interstitial_open_every_n' => 3,
                'interstitial_on_exit' => false,
                'interstitial_every_n_calcs' => 3,
                'interstitial_min_interval_sec' => 60,
                'app_open_min_interval_sec' => 240,
                'first_interstitial_delay_sec' => 20,
                'reward_ad_free_minutes' => 120,
                'banner_on_home' => true,
                'banner_on_calculator' => true,
                'banner_on_result' => false,
            ],
            'app' => [
                'latest_version_code' => 0,
                'force_update_below' => 0,
                'update_message' => 'A new version is available with new features and fixes.',
                'popup' => [
                    'enabled' => false, 'id' => '', 'title' => '', 'body' => '', 'image_url' => '',
                    'primary_label' => 'OK', 'primary_action' => 'dismiss', 'primary_value' => '', 'secondary_label' => '',
                    'frequency' => 'once', 'audience' => 'all', 'delay_sec' => 3,
                    'min_version_code' => 0, 'max_version_code' => 0, 'start_date' => '', 'end_date' => '',
                ],
                'onesignal_app_id' => '',
                'privacy_url' => '',
                'terms_url' => '',
                'contact_email' => '',
                'share_text' => '',
                'more_apps_url' => '',
            ],
            'features' => [
                'cloud_save' => true,
                'analytics' => true,
                'rate_prompt' => true,
                'rate_prompt_after_calcs' => 8,
                'in_app_update' => true,
                'currency_api_url' => 'https://api.frankfurter.app/latest',
                'push_enabled' => true,
                'push_prompt_enabled' => true,
                'push_prompt_after_opens' => 2,
                'in_app_messages' => true,
            ],
        ];
    }

    public static function load(): array
    {
        $row = Db::row('SELECT json, updated_at FROM app_config WHERE id = 1');
        $stored = $row ? json_decode($row['json'], true) : null;
        $cfg = self::merge(self::defaults(), is_array($stored) ? $stored : []);
        $cfg['updated_at'] = $row ? (int)$row['updated_at'] : 0;
        return $cfg;
    }

    public static function save(array $input): array
    {
        $cfg = self::merge(self::defaults(), $input);
        unset($cfg['updated_at']);
        self::validate($cfg);
        $json = json_encode($cfg, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        $exists = Db::value('SELECT COUNT(*) FROM app_config WHERE id = 1');
        if ($exists) Db::exec('UPDATE app_config SET json = ?, updated_at = ? WHERE id = 1', [$json, time()]);
        else Db::exec('INSERT INTO app_config (id, json, updated_at) VALUES (1, ?, ?)', [$json, time()]);
        return self::load();
    }

    /** Deep-merge $over onto $base keeping only keys that exist in $base (so unknown keys are dropped) and coercing types. */
    private static function merge(array $base, array $over): array
    {
        foreach ($base as $k => $v) {
            if (!array_key_exists($k, $over)) continue;
            $o = $over[$k];
            if (is_array($v) && array_keys($v) !== range(0, count($v) - 1)) { // assoc
                $base[$k] = is_array($o) ? self::merge($v, $o) : $v;
            } elseif (is_array($v)) { // list
                $base[$k] = is_array($o) ? array_values(array_map('strval', $o)) : $v;
            } elseif (is_bool($v)) {
                $base[$k] = filter_var($o, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE) ?? $v;
            } elseif (is_int($v)) {
                $base[$k] = is_numeric($o) ? (int)$o : $v;
            } else {
                $base[$k] = is_scalar($o) ? trim((string)$o) : $v;
            }
        }
        return $base;
    }

    private static function validate(array $cfg): void
    {
        $allowed = ['admob', 'max', 'meta'];
        foreach ($cfg['ads']['priority'] as $p) {
            if (!in_array($p, $allowed, true)) throw new HttpException(422, "Unknown ad network in priority: $p");
        }
        if (count(array_unique($cfg['ads']['priority'])) !== count($cfg['ads']['priority'])) {
            throw new HttpException(422, 'Duplicate network in priority list');
        }
        if ($cfg['ads']['interstitial_every_n_calcs'] < 1) throw new HttpException(422, 'interstitial_every_n_calcs must be >= 1');
        if ($cfg['ads']['interstitial_open_every_n'] < 1) throw new HttpException(422, 'interstitial_open_every_n must be >= 1');
        $p = $cfg['app']['popup'];
        if (!in_array($p['primary_action'], ['dismiss', 'url', 'calculator', 'rate', 'share', 'update', 'notifications'], true)) throw new HttpException(422, 'Unknown pop-up action: ' . $p['primary_action']);
        if (!in_array($p['frequency'], ['once', 'daily', 'every_open'], true)) throw new HttpException(422, 'Unknown pop-up frequency: ' . $p['frequency']);
        if (!in_array($p['audience'], ['all', 'new', 'returning'], true)) throw new HttpException(422, 'Unknown pop-up audience: ' . $p['audience']);
        foreach (['start_date', 'end_date'] as $k) {
            if ($p[$k] !== '' && !preg_match('/^\d{4}-\d{2}-\d{2}$/', $p[$k])) throw new HttpException(422, "Pop-up $k must be YYYY-MM-DD");
        }
        if ($p['enabled'] && $p['id'] === '') throw new HttpException(422, 'Give the pop-up an id (change it to show the pop-up again)');
        if ($p['primary_action'] === 'url' && $p['enabled'] && !preg_match('#^https?://#', $p['primary_value'])) throw new HttpException(422, 'Pop-up button URL must start with http:// or https://');
        $os = $cfg['app']['onesignal_app_id'];
        if ($os !== '' && !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i', $os)) throw new HttpException(422, 'OneSignal app ID must look like xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx');
        foreach (['interstitial_min_interval_sec', 'app_open_min_interval_sec', 'first_interstitial_delay_sec', 'reward_ad_free_minutes', 'rate_prompt_after_calcs'] as $k) {
            $v = $cfg['ads'][$k] ?? $cfg['features'][$k] ?? 0;
            if ($v < 0) throw new HttpException(422, "$k must be >= 0");
        }
    }
}

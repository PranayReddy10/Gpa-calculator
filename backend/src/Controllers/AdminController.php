<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\AppConfig;
use Gpa\Auth;
use Gpa\Db;
use Gpa\HttpException;
use Gpa\Request;
use Gpa\Response;

final class AdminController
{
    public static function login(Request $r): Response
    {
        $u = trim($r->str('username'));
        $p = $r->str('password');
        if ($u === '' || $p === '') throw new HttpException(422, 'username and password are required');
        return Response::ok(Auth::login($u, $p));
    }

    public static function logout(Request $r): Response
    {
        Auth::requireAdmin($r);
        Auth::logout($r->bearer());
        return Response::ok(true);
    }

    public static function me(Request $r): Response
    {
        $a = Auth::requireAdmin($r);
        return Response::ok(['username' => $a['username'], 'expires_at' => (int)$a['expires_at']]);
    }

    public static function password(Request $r): Response
    {
        $a = Auth::requireAdmin($r);
        $new = $r->str('new_password');
        if (strlen($new) < 8) throw new HttpException(422, 'Password must be at least 8 characters');
        $u = Db::row('SELECT * FROM admin_users WHERE id = ?', [$a['id']]);
        if (!password_verify($r->str('old_password'), $u['password_hash'])) throw new HttpException(401, 'Current password is wrong');
        Auth::createAdmin($u['username'], $new);
        return Response::ok(true);
    }

    public static function getConfig(Request $r): Response
    {
        Auth::requireAdmin($r);
        return Response::ok(AppConfig::load());
    }

    public static function putConfig(Request $r): Response
    {
        Auth::requireAdmin($r);
        if (!$r->body) throw new HttpException(422, 'JSON config body required');
        return Response::ok(AppConfig::save($r->body));
    }

    /** GET /api/v1/admin/stats — everything the dashboard needs in one call. */
    public static function stats(Request $r): Response
    {
        Auth::requireAdmin($r);
        $now = time();
        $today = gmdate('Y-m-d', $now);
        $d7 = gmdate('Y-m-d', $now - 6 * 86400);
        $d30 = gmdate('Y-m-d', $now - 29 * 86400);
        $startToday = strtotime($today . ' 00:00:00 UTC');

        $overview = [
            'total_devices' => (int)Db::value('SELECT COUNT(*) FROM devices'),
            'new_today' => (int)Db::value('SELECT COUNT(*) FROM devices WHERE first_seen >= ?', [$startToday]),
            'new_7d' => (int)Db::value('SELECT COUNT(*) FROM devices WHERE first_seen >= ?', [$now - 7 * 86400]),
            'new_30d' => (int)Db::value('SELECT COUNT(*) FROM devices WHERE first_seen >= ?', [$now - 30 * 86400]),
            'dau' => (int)Db::value('SELECT COUNT(DISTINCT device_id) FROM device_days WHERE day = ?', [$today]),
            'wau' => (int)Db::value('SELECT COUNT(DISTINCT device_id) FROM device_days WHERE day >= ?', [$d7]),
            'mau' => (int)Db::value('SELECT COUNT(DISTINCT device_id) FROM device_days WHERE day >= ?', [$d30]),
            'active_now' => (int)Db::value('SELECT COUNT(*) FROM devices WHERE last_seen >= ?', [$now - 300]),
            'calcs_today' => (int)Db::value("SELECT COUNT(*) FROM events WHERE name = 'calc_used' AND ts >= ?", [$startToday]),
            'calcs_7d' => (int)Db::value("SELECT COUNT(*) FROM events WHERE name = 'calc_used' AND ts >= ?", [$now - 7 * 86400]),
            'calcs_30d' => (int)Db::value("SELECT COUNT(*) FROM events WHERE name = 'calc_used' AND ts >= ?", [$now - 30 * 86400]),
            'saved_total' => (int)Db::value('SELECT COUNT(*) FROM saved_calculations WHERE deleted = 0'),
            'ad_impressions_7d' => (int)Db::value("SELECT COUNT(*) FROM events WHERE name = 'ad_impression' AND ts >= ?", [$now - 7 * 86400]),
        ];

        // Top calculators (30 days) — params JSON holds {"calc":"id"}; extract in PHP for portability.
        $top = [];
        foreach (Db::rows("SELECT params, COUNT(*) AS n FROM events WHERE name = 'calc_used' AND ts >= ? GROUP BY params ORDER BY n DESC LIMIT 60", [$now - 30 * 86400]) as $row) {
            $p = json_decode((string)$row['params'], true);
            $calc = is_array($p) && isset($p['calc']) ? (string)$p['calc'] : 'unknown';
            $top[$calc] = ($top[$calc] ?? 0) + (int)$row['n'];
        }
        arsort($top);
        $topCalcs = [];
        foreach (array_slice($top, 0, 15, true) as $k => $v) $topCalcs[] = ['calc' => $k, 'count' => $v];

        $ads = [];
        foreach (Db::rows("SELECT params, COUNT(*) AS n FROM events WHERE name = 'ad_impression' AND ts >= ? GROUP BY params", [$now - 7 * 86400]) as $row) {
            $p = json_decode((string)$row['params'], true);
            $key = (is_array($p) ? ($p['network'] ?? '?') . ' / ' . ($p['format'] ?? '?') : '?');
            $ads[$key] = ($ads[$key] ?? 0) + (int)$row['n'];
        }
        arsort($ads);
        $adRows = [];
        foreach ($ads as $k => $v) $adRows[] = ['key' => $k, 'count' => $v];

        $versions = Db::rows('SELECT app_version AS v, app_version_code AS code, COUNT(*) AS n FROM devices WHERE last_seen >= ? GROUP BY app_version, app_version_code ORDER BY n DESC LIMIT 10', [$now - 30 * 86400]);
        $countries = Db::rows('SELECT country, COUNT(*) AS n FROM devices GROUP BY country ORDER BY n DESC LIMIT 12');
        $os = Db::rows('SELECT os_version AS v, COUNT(*) AS n FROM devices GROUP BY os_version ORDER BY n DESC LIMIT 10');
        $models = Db::rows('SELECT manufacturer, model, COUNT(*) AS n FROM devices GROUP BY manufacturer, model ORDER BY n DESC LIMIT 10');

        // 30-day daily series
        $series = [];
        for ($i = 29; $i >= 0; $i--) {
            $day = gmdate('Y-m-d', $now - $i * 86400);
            $series[$day] = ['day' => $day, 'active' => 0, 'new' => 0, 'calcs' => 0];
        }
        foreach (Db::rows('SELECT day, COUNT(*) AS n FROM device_days WHERE day >= ? GROUP BY day', [$d30]) as $row) {
            if (isset($series[$row['day']])) $series[$row['day']]['active'] = (int)$row['n'];
        }
        foreach (Db::rows('SELECT first_seen FROM devices WHERE first_seen >= ?', [$now - 30 * 86400]) as $row) {
            $day = gmdate('Y-m-d', (int)$row['first_seen']);
            if (isset($series[$day])) $series[$day]['new']++;
        }
        foreach (Db::rows("SELECT ts FROM events WHERE name = 'calc_used' AND ts >= ?", [$now - 30 * 86400]) as $row) {
            $day = gmdate('Y-m-d', (int)$row['ts']);
            if (isset($series[$day])) $series[$day]['calcs']++;
        }

        return Response::ok([
            'generated_at' => $now,
            'overview' => $overview,
            'top_calculators' => $topCalcs,
            'ad_impressions' => $adRows,
            'versions' => $versions,
            'countries' => $countries,
            'os_versions' => $os,
            'models' => $models,
            'daily' => array_values($series),
        ]);
    }

    public static function devices(Request $r): Response
    {
        Auth::requireAdmin($r);
        $page = max(1, (int)($r->query['page'] ?? 1));
        $per = 50;
        $rows = Db::rows('SELECT device_id, manufacturer, model, os_version, app_version, app_version_code, country, language, first_seen, last_seen, open_count FROM devices ORDER BY last_seen DESC LIMIT ' . $per . ' OFFSET ' . (($page - 1) * $per));
        foreach ($rows as &$row) {
            $row['device_id'] = substr((string)$row['device_id'], 0, 8) . '…';
        }
        return Response::ok(['page' => $page, 'per_page' => $per, 'total' => (int)Db::value('SELECT COUNT(*) FROM devices'), 'items' => $rows]);
    }

    public static function events(Request $r): Response
    {
        Auth::requireAdmin($r);
        $name = preg_replace('/[^a-z0-9_]/', '', strtolower((string)($r->query['name'] ?? '')));
        $limit = min(500, max(1, (int)($r->query['limit'] ?? 100)));
        $sql = 'SELECT device_id, name, params, app_version_code, ts FROM events';
        $params = [];
        if ($name !== '') { $sql .= ' WHERE name = ?'; $params[] = $name; }
        $sql .= ' ORDER BY id DESC LIMIT ' . $limit;
        $rows = Db::rows($sql, $params);
        foreach ($rows as &$row) $row['device_id'] = substr((string)$row['device_id'], 0, 8) . '…';
        $names = Db::rows('SELECT name, COUNT(*) AS n FROM events WHERE ts >= ? GROUP BY name ORDER BY n DESC', [time() - 7 * 86400]);
        return Response::ok(['items' => $rows, 'names' => $names]);
    }
}

<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\Auth;
use Gpa\Db;
use Gpa\HttpException;
use Gpa\Request;
use Gpa\Response;

final class EventsController
{
    private const MAX_BATCH = 200;

    /** POST /api/v1/events — batch ingest {device_id, app_version_code, events:[{name, ts, params}]} */
    public static function ingest(Request $r): Response
    {
        Auth::requireAppKey($r);
        $deviceId = DeviceController::deviceId($r);
        $events = $r->body['events'] ?? null;
        if (!is_array($events)) throw new HttpException(422, 'events must be an array');
        if (count($events) > self::MAX_BATCH) $events = array_slice($events, 0, self::MAX_BATCH);
        $versionCode = $r->int('app_version_code', 0) ?: null;
        $now = time();
        $pdo = Db::pdo();
        $pdo->beginTransaction();
        try {
            $st = $pdo->prepare('INSERT INTO events (device_id, name, params, app_version_code, ts) VALUES (?, ?, ?, ?, ?)');
            $n = 0;
            foreach ($events as $e) {
                if (!is_array($e) || empty($e['name']) || !is_string($e['name'])) continue;
                $name = mb_substr(preg_replace('/[^a-z0-9_]/', '', strtolower($e['name'])), 0, 64);
                if ($name === '') continue;
                $ts = isset($e['ts']) && is_numeric($e['ts']) ? (int)$e['ts'] : $now;
                if ($ts > $now + 300 || $ts < $now - 30 * 86400) $ts = $now; // clamp clock skew
                $params = isset($e['params']) && is_array($e['params']) ? json_encode($e['params'], JSON_UNESCAPED_UNICODE) : null;
                if ($params !== null && strlen($params) > 2000) $params = substr($params, 0, 2000);
                $st->execute([$deviceId, $name, $params, $versionCode, $ts]);
                $n++;
            }
            $pdo->commit();
        } catch (\Throwable $t) {
            $pdo->rollBack();
            throw $t;
        }
        Db::exec('UPDATE devices SET last_seen = ? WHERE device_id = ?', [$now, $deviceId]);
        DeviceController::touchDay($deviceId, $now);
        self::maybePurge();
        return Response::ok(['accepted' => $n]);
    }

    /** Cheap probabilistic retention cleanup so no cron job is needed on shared hosting. */
    private static function maybePurge(): void
    {
        if (random_int(1, 200) !== 1) return;
        $days = (int)Db::config('EVENT_RETENTION_DAYS', 90);
        if ($days <= 0) return;
        Db::exec('DELETE FROM events WHERE ts < ?', [time() - $days * 86400]);
        Db::exec('DELETE FROM device_days WHERE day < ?', [gmdate('Y-m-d', time() - 400 * 86400)]);
    }
}

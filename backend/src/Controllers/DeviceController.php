<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\Auth;
use Gpa\Db;
use Gpa\HttpException;
use Gpa\Request;
use Gpa\Response;

final class DeviceController
{
    private const FIELDS = ['manufacturer', 'model', 'os_version', 'sdk_int', 'app_version', 'app_version_code', 'language', 'country', 'timezone', 'first_open'];

    /** POST /api/v1/device/register — upsert; returns the device token used for saved-calculation sync. */
    public static function register(Request $r): Response
    {
        Auth::requireAppKey($r);
        $deviceId = self::deviceId($r);
        $now = time();
        $vals = [];
        foreach (self::FIELDS as $f) {
            $v = $r->body[$f] ?? null;
            if ($v === null) continue;
            $vals[$f] = in_array($f, ['sdk_int', 'app_version_code', 'first_open'], true) ? (int)$v : mb_substr(trim((string)$v), 0, 96);
        }
        $vals['ip'] = mb_substr($r->ip, 0, 45);
        $existing = Db::row('SELECT id, token, open_count FROM devices WHERE device_id = ?', [$deviceId]);
        if ($existing) {
            $set = ['last_seen = ?', 'open_count = open_count + 1'];
            $params = [$now];
            foreach ($vals as $k => $v) { $set[] = "$k = ?"; $params[] = $v; }
            $params[] = $existing['id'];
            Db::exec('UPDATE devices SET ' . implode(', ', $set) . ' WHERE id = ?', $params);
            $token = $existing['token'];
        } else {
            $token = bin2hex(random_bytes(24));
            $cols = ['device_id', 'token', 'first_seen', 'last_seen', 'open_count'];
            $params = [$deviceId, $token, $now, $now, 1];
            foreach ($vals as $k => $v) { $cols[] = $k; $params[] = $v; }
            Db::insert('INSERT INTO devices (' . implode(', ', $cols) . ') VALUES (' . implode(', ', array_fill(0, count($cols), '?')) . ')', $params);
        }
        self::touchDay($deviceId, $now);
        return Response::ok(['token' => $token]);
    }

    public static function deviceId(Request $r): string
    {
        $id = trim($r->str('device_id', $r->header('X-Device-Id')));
        if ($id === '' || strlen($id) > 64 || !preg_match('/^[A-Za-z0-9\-_]+$/', $id)) {
            throw new HttpException(422, 'device_id is required');
        }
        return $id;
    }

    /** Records that the device was active on this (UTC) day — powers DAU/WAU/MAU without scanning events. */
    public static function touchDay(string $deviceId, int $ts): void
    {
        $day = gmdate('Y-m-d', $ts);
        $exists = Db::value('SELECT 1 FROM device_days WHERE device_id = ? AND day = ?', [$deviceId, $day]);
        if ($exists) return;
        try {
            Db::exec('INSERT INTO device_days (device_id, day) VALUES (?, ?)', [$deviceId, $day]);
        } catch (\PDOException $e) {
            // concurrent insert of the same (device, day) — ignore
        }
    }
}

<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\Auth;
use Gpa\Db;
use Gpa\HttpException;
use Gpa\Request;
use Gpa\Response;

/** Saved calculations: the user's history mirrored to MySQL, keyed by the device token. */
final class SavesController
{
    private const MAX_PER_DEVICE = 2000;

    /** GET /api/v1/saves */
    public static function list(Request $r): Response
    {
        Auth::requireAppKey($r);
        $d = Auth::requireDevice($r);
        $rows = Db::rows('SELECT id, local_id, calc_id, title, inputs, results, summary, created_at FROM saved_calculations WHERE device_id = ? AND deleted = 0 ORDER BY created_at DESC LIMIT 1000', [$d['device_id']]);
        foreach ($rows as &$row) {
            $row['id'] = (int)$row['id'];
            $row['local_id'] = (int)$row['local_id'];
            $row['created_at'] = (int)$row['created_at'];
        }
        return Response::ok($rows);
    }

    /** POST /api/v1/saves {items:[{local_id, calc_id, title, inputs, results, summary, created_at}]} */
    public static function upsert(Request $r): Response
    {
        Auth::requireAppKey($r);
        $d = Auth::requireDevice($r);
        $items = $r->body['items'] ?? null;
        if (!is_array($items)) throw new HttpException(422, 'items must be an array');
        $out = [];
        $count = (int)Db::value('SELECT COUNT(*) FROM saved_calculations WHERE device_id = ? AND deleted = 0', [$d['device_id']]);
        foreach (array_slice($items, 0, 100) as $it) {
            if (!is_array($it) || empty($it['calc_id'])) continue;
            $localId = isset($it['local_id']) ? (int)$it['local_id'] : 0;
            $createdAt = isset($it['created_at']) ? (int)$it['created_at'] : time();
            $existing = Db::row('SELECT id FROM saved_calculations WHERE device_id = ? AND local_id = ? AND created_at = ?', [$d['device_id'], $localId, $createdAt]);
            if ($existing) {
                $out[] = ['local_id' => $localId, 'id' => (int)$existing['id']];
                continue;
            }
            if ($count >= self::MAX_PER_DEVICE) {
                // keep the newest: drop the oldest row to make room
                $oldest = Db::row('SELECT id FROM saved_calculations WHERE device_id = ? AND deleted = 0 ORDER BY created_at ASC LIMIT 1', [$d['device_id']]);
                if ($oldest) Db::exec('DELETE FROM saved_calculations WHERE id = ?', [$oldest['id']]);
            } else {
                $count++;
            }
            $id = Db::insert('INSERT INTO saved_calculations (device_id, local_id, calc_id, title, inputs, results, summary, created_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)', [
                $d['device_id'], $localId,
                mb_substr((string)$it['calc_id'], 0, 64),
                mb_substr((string)($it['title'] ?? ''), 0, 128),
                self::clip($it['inputs'] ?? ''),
                self::clip($it['results'] ?? ''),
                mb_substr((string)($it['summary'] ?? ''), 0, 255),
                $createdAt,
            ]);
            $out[] = ['local_id' => $localId, 'id' => $id];
        }
        return Response::ok($out);
    }

    private static function clip($v): string
    {
        $s = is_string($v) ? $v : json_encode($v);
        return strlen($s) > 20000 ? substr($s, 0, 20000) : $s;
    }

    /** DELETE /api/v1/saves/{id} */
    public static function delete(Request $r, string $id): Response
    {
        Auth::requireAppKey($r);
        $d = Auth::requireDevice($r);
        Db::exec('UPDATE saved_calculations SET deleted = 1 WHERE id = ? AND device_id = ?', [(int)$id, $d['device_id']]);
        return Response::ok(['deleted' => (int)$id]);
    }

    /** DELETE /api/v1/saves */
    public static function deleteAll(Request $r): Response
    {
        Auth::requireAppKey($r);
        $d = Auth::requireDevice($r);
        $n = Db::exec('UPDATE saved_calculations SET deleted = 1 WHERE device_id = ? AND deleted = 0', [$d['device_id']]);
        return Response::ok(['deleted' => $n]);
    }
}

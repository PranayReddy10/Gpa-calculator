<?php
declare(strict_types=1);

namespace Gpa;

use Gpa\Controllers\AdminController;
use Gpa\Controllers\ConfigController;
use Gpa\Controllers\DeviceController;
use Gpa\Controllers\EventsController;
use Gpa\Controllers\SavesController;

/** Tiny router. Paths are relative to the backend folder, e.g. /api/v1/config. */
final class App
{
    public static function boot(array $config): void
    {
        Db::init($config);
    }

    public static function handle(Request $r): Response
    {
        try {
            return self::route($r);
        } catch (HttpException $e) {
            return Response::error($e->status, $e->getMessage());
        } catch (\PDOException $e) {
            error_log('DB error: ' . $e->getMessage());
            return Response::error(500, 'Database error');
        } catch (\Throwable $e) {
            error_log('Unhandled: ' . $e->getMessage());
            return Response::error(500, 'Server error');
        }
    }

    private static function route(Request $r): Response
    {
        $m = $r->method;
        $p = $r->path;
        if ($m === 'OPTIONS') return new Response(204, ['ok' => true]);
        if ($p === '/' || $p === '/api' || $p === '/api/v1') return Response::ok(['name' => 'GPA Calculator API', 'version' => 1, 'time' => time()]);

        // ---- public pages (no app key): privacy policy for Google Play, app-ads.txt for AdMob
        if ($m === 'GET' && in_array($p, ['/privacy', '/privacy-policy', '/privacy.html'], true)) return Pages::privacy();
        if ($m === 'GET' && $p === '/app-ads.txt') return Pages::appAdsTxt();

        // ---- app endpoints
        if ($p === '/api/v1/config' && $m === 'GET') return ConfigController::get($r);
        if ($p === '/api/v1/device/register' && $m === 'POST') return DeviceController::register($r);
        if ($p === '/api/v1/events' && $m === 'POST') return EventsController::ingest($r);
        if ($p === '/api/v1/saves' && $m === 'GET') return SavesController::list($r);
        if ($p === '/api/v1/saves' && $m === 'POST') return SavesController::upsert($r);
        if ($p === '/api/v1/saves' && $m === 'DELETE') return SavesController::deleteAll($r);
        if (preg_match('#^/api/v1/saves/(\d+)$#', $p, $mm) && $m === 'DELETE') return SavesController::delete($r, $mm[1]);

        // ---- admin endpoints
        if ($p === '/api/v1/admin/login' && $m === 'POST') return AdminController::login($r);
        if ($p === '/api/v1/admin/logout' && $m === 'POST') return AdminController::logout($r);
        if ($p === '/api/v1/admin/me' && $m === 'GET') return AdminController::me($r);
        if ($p === '/api/v1/admin/password' && $m === 'POST') return AdminController::password($r);
        if ($p === '/api/v1/admin/config' && $m === 'GET') return AdminController::getConfig($r);
        if ($p === '/api/v1/admin/config' && ($m === 'PUT' || $m === 'POST')) return AdminController::putConfig($r);
        if ($p === '/api/v1/admin/stats' && $m === 'GET') return AdminController::stats($r);
        if ($p === '/api/v1/admin/devices' && $m === 'GET') return AdminController::devices($r);
        if ($p === '/api/v1/admin/events' && $m === 'GET') return AdminController::events($r);
        if ($p === '/api/v1/admin/privacy/template' && $m === 'GET') { Auth::requireAdmin($r); return Response::ok(['markdown' => Pages::defaultPrivacyMarkdown(AppConfig::load())]); }
        if ($p === '/api/v1/admin/push/status' && $m === 'GET') return AdminController::pushStatus($r);
        if ($p === '/api/v1/admin/push' && $m === 'GET') return AdminController::pushHistory($r);
        if ($p === '/api/v1/admin/push' && $m === 'POST') return AdminController::pushSend($r);

        return Response::error(404, 'Not found: ' . $m . ' ' . $p);
    }
}

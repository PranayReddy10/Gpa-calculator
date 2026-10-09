<?php
declare(strict_types=1);

namespace Gpa\Controllers;

use Gpa\AppConfig;
use Gpa\Auth;
use Gpa\Request;
use Gpa\Response;

final class ConfigController
{
    /** GET /api/v1/config — public (app key), consumed by the Android app on launch. */
    public static function get(Request $r): Response
    {
        Auth::requireAppKey($r);
        $cfg = AppConfig::load();
        // Secrets the client never needs are not exposed (admin sees them via the admin endpoint).
        unset($cfg['ads']['max']['sdk_key']);
        return Response::ok($cfg);
    }
}

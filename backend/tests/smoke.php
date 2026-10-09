<?php
/**
 * In-process smoke test of the whole API against a throw-away SQLite database.
 * Run: php backend/tests/smoke.php
 */
declare(strict_types=1);

require __DIR__ . '/../src/autoload.php';

use Gpa\App;
use Gpa\AppConfig;
use Gpa\Auth;
use Gpa\Db;
use Gpa\Request;

$dbFile = sys_get_temp_dir() . '/gpa_smoke_' . getmypid() . '.sqlite';
@unlink($dbFile);
$config = [
    'DB_DRIVER' => 'sqlite', 'DB_SQLITE' => $dbFile,
    'APP_API_KEY' => 'test-app-key', 'ADMIN_TOKEN_TTL' => 3600, 'EVENT_RETENTION_DAYS' => 90,
];
App::boot($config);
Db::migrate();
Auth::createAdmin('admin', 'secret-pass-123');
AppConfig::save(AppConfig::load());

$failures = 0;
function call(string $method, string $path, array $body = [], array $headers = [], array $query = []): array {
    $res = App::handle(new Request($method, $path, $headers, $query, $body, '127.0.0.1'));
    return [$res->status, $res->payload];
}
function check(string $name, bool $cond, $detail = null): void {
    global $failures;
    if ($cond) { echo "  ok   $name\n"; return; }
    $failures++;
    echo "  FAIL $name " . ($detail === null ? '' : json_encode($detail)) . "\n";
}
$APP = ['X-App-Key' => 'test-app-key'];

echo "config\n";
[$s, $p] = call('GET', '/api/v1/config');
check('config requires app key', $s === 401);
[$s, $p] = call('GET', '/api/v1/config', [], $APP);
check('config ok', $s === 200 && $p['ok'] && $p['data']['ads']['priority'] === ['admob', 'max', 'meta']);
check('config hides MAX sdk key', !isset($p['data']['ads']['max']['sdk_key']));

echo "device\n";
$dev = ['device_id' => 'abc-123', 'model' => 'Pixel 8', 'manufacturer' => 'Google', 'os_version' => '15', 'sdk_int' => 35, 'app_version' => '2.0.0', 'app_version_code' => 100, 'language' => 'en', 'country' => 'IN', 'timezone' => 'Asia/Kolkata', 'first_open' => time()];
[$s, $p] = call('POST', '/api/v1/device/register', $dev, $APP);
check('register ok', $s === 200 && strlen($p['data']['token']) === 48, $p);
$token = $p['data']['token'];
[$s, $p2] = call('POST', '/api/v1/device/register', $dev, $APP);
check('register is idempotent (same token)', $p2['data']['token'] === $token);
check('open_count increments', (int)Db::value('SELECT open_count FROM devices WHERE device_id = ?', ['abc-123']) === 2);
[$s, $p] = call('POST', '/api/v1/device/register', ['device_id' => 'bad id!'], $APP);
check('register rejects bad id', $s === 422);

echo "events\n";
$events = ['device_id' => 'abc-123', 'app_version_code' => 100, 'events' => [
    ['name' => 'app_open', 'ts' => time()],
    ['name' => 'calc_used', 'ts' => time(), 'params' => ['calc' => 'marks_percent']],
    ['name' => 'calc_used', 'ts' => time(), 'params' => ['calc' => 'emi']],
    ['name' => 'calc_used', 'ts' => time(), 'params' => ['calc' => 'emi']],
    ['name' => 'ad_impression', 'ts' => time(), 'params' => ['network' => 'admob', 'format' => 'banner_home']],
    ['name' => 'Bad Name!!', 'ts' => time()],
    ['nope' => 1],
]];
[$s, $p] = call('POST', '/api/v1/events', $events, $APP);
check('events accepted (sanitised)', $s === 200 && $p['data']['accepted'] === 6, $p);
check('device day recorded', (int)Db::value('SELECT COUNT(*) FROM device_days') === 1);
[$s, $p] = call('POST', '/api/v1/events', ['device_id' => 'abc-123', 'events' => 'x'], $APP);
check('events validates array', $s === 422);

echo "saves\n";
$DEV = $APP + ['Authorization' => 'Bearer ' . $token];
[$s, $p] = call('GET', '/api/v1/saves', [], $APP);
check('saves need device token', $s === 401);
$items = ['items' => [
    ['local_id' => 1, 'calc_id' => 'marks_percent', 'title' => 'Marks %', 'inputs' => '{"obtained":"450"}', 'results' => '[]', 'summary' => '90%', 'created_at' => 1700000000],
    ['local_id' => 2, 'calc_id' => 'emi', 'title' => 'EMI', 'inputs' => '{}', 'results' => '[]', 'summary' => '8,678', 'created_at' => 1700000100],
]];
[$s, $p] = call('POST', '/api/v1/saves', $items, $DEV);
check('saves upsert', $s === 200 && count($p['data']) === 2 && $p['data'][0]['local_id'] === 1, $p);
$remoteId = $p['data'][0]['id'];
[$s, $p] = call('POST', '/api/v1/saves', $items, $DEV);
check('saves upsert dedupes', $p['data'][0]['id'] === $remoteId && (int)Db::value('SELECT COUNT(*) FROM saved_calculations') === 2);
[$s, $p] = call('GET', '/api/v1/saves', [], $DEV);
check('saves list newest first', $s === 200 && count($p['data']) === 2 && $p['data'][0]['calc_id'] === 'emi', $p);
[$s, $p] = call('DELETE', '/api/v1/saves/' . $remoteId, [], $DEV);
check('saves delete one', $s === 200);
[$s, $p] = call('GET', '/api/v1/saves', [], $DEV);
check('deleted row hidden', count($p['data']) === 1);
[$s, $p] = call('DELETE', '/api/v1/saves', [], $DEV);
check('saves delete all', $s === 200 && $p['data']['deleted'] === 1);

echo "admin\n";
[$s, $p] = call('POST', '/api/v1/admin/login', ['username' => 'admin', 'password' => 'wrong']);
check('login rejects bad password', $s === 401);
[$s, $p] = call('POST', '/api/v1/admin/login', ['username' => 'admin', 'password' => 'secret-pass-123']);
check('login ok', $s === 200 && !empty($p['data']['token']), $p);
$ADM = ['Authorization' => 'Bearer ' . $p['data']['token']];
[$s, $p] = call('GET', '/api/v1/admin/stats', [], []);
check('stats need admin', $s === 401);
[$s, $p] = call('GET', '/api/v1/admin/stats', [], $ADM);
$o = $p['data']['overview'] ?? [];
check('stats overview', $s === 200 && $o['total_devices'] === 1 && $o['dau'] === 1 && $o['calcs_today'] === 3 && $o['ad_impressions_7d'] === 1, $o);
check('stats top calculators', $p['data']['top_calculators'][0]['calc'] === 'emi' && $p['data']['top_calculators'][0]['count'] === 2, $p['data']['top_calculators']);
check('stats daily series has 30 days', count($p['data']['daily']) === 30 && end($p['data']['daily'])['active'] === 1);
check('stats ad rows', $p['data']['ad_impressions'][0]['key'] === 'admob / banner_home');

[$s, $p] = call('GET', '/api/v1/admin/config', [], $ADM);
check('admin config includes sdk key field', $s === 200 && array_key_exists('sdk_key', $p['data']['ads']['max']));
$cfg = $p['data'];
$cfg['ads']['priority'] = ['meta', 'admob'];
$cfg['ads']['test_mode'] = false;
$cfg['ads']['interstitial_every_n_calcs'] = '5';
$cfg['ads']['admob']['banner'] = 'ca-app-pub-1/2';
$cfg['app']['announcement']['enabled'] = true;
$cfg['app']['announcement']['id'] = 'v2';
$cfg['unknown_key'] = 'dropped';
[$s, $p] = call('PUT', '/api/v1/admin/config', $cfg, $ADM);
check('admin config saves', $s === 200 && $p['data']['ads']['priority'] === ['meta', 'admob'] && $p['data']['ads']['test_mode'] === false && $p['data']['ads']['interstitial_every_n_calcs'] === 5 && !isset($p['data']['unknown_key']), $p);
[$s, $p] = call('GET', '/api/v1/config', [], $APP);
check('app sees new config', $p['data']['ads']['admob']['banner'] === 'ca-app-pub-1/2' && $p['data']['app']['announcement']['id'] === 'v2');
$cfg['ads']['priority'] = ['admob', 'bogus'];
[$s, $p] = call('PUT', '/api/v1/admin/config', $cfg, $ADM);
check('admin config validates networks', $s === 422);

[$s, $p] = call('GET', '/api/v1/admin/devices', [], $ADM);
check('admin devices', $s === 200 && $p['data']['total'] === 1 && $p['data']['items'][0]['model'] === 'Pixel 8');
[$s, $p] = call('GET', '/api/v1/admin/events', [], $ADM, ['name' => 'calc_used']);
check('admin events filter', $s === 200 && count($p['data']['items']) === 3);
[$s, $p] = call('POST', '/api/v1/admin/password', ['old_password' => 'secret-pass-123', 'new_password' => 'another-pass-456'], $ADM);
check('password change', $s === 200);
[$s, $p] = call('POST', '/api/v1/admin/login', ['username' => 'admin', 'password' => 'another-pass-456']);
check('login with new password', $s === 200);
[$s, $p] = call('POST', '/api/v1/admin/logout', [], $ADM);
check('logout', $s === 200);
[$s, $p] = call('GET', '/api/v1/admin/me', [], $ADM);
check('token invalid after logout', $s === 401);
[$s, $p] = call('GET', '/api/v1/nope');
check('404', $s === 404);

@unlink($dbFile); @unlink($dbFile . '-wal'); @unlink($dbFile . '-shm');
echo $failures === 0 ? "\nALL PASSED\n" : "\n$failures FAILED\n";
exit($failures === 0 ? 0 : 1);

<?php
declare(strict_types=1);

namespace Gpa;

final class Auth
{
    /** Every app endpoint must carry the shared app key. */
    public static function requireAppKey(Request $r): void
    {
        $expected = (string)Db::config('APP_API_KEY', '');
        $given = $r->header('X-App-Key');
        if ($expected === '' || !hash_equals($expected, $given)) {
            throw new HttpException(401, 'Invalid app key');
        }
    }

    /** Returns the device row for the Bearer device token. */
    public static function requireDevice(Request $r): array
    {
        $token = $r->bearer();
        if ($token === '') throw new HttpException(401, 'Device token required');
        $d = Db::row('SELECT * FROM devices WHERE token = ?', [$token]);
        if (!$d) throw new HttpException(401, 'Unknown device token');
        return $d;
    }

    public static function requireAdmin(Request $r): array
    {
        $token = $r->bearer();
        if ($token === '') throw new HttpException(401, 'Admin login required');
        $t = Db::row('SELECT t.token, t.expires_at, u.id, u.username FROM admin_tokens t JOIN admin_users u ON u.id = t.admin_id WHERE t.token = ?', [$token]);
        if (!$t || (int)$t['expires_at'] < time()) {
            if ($t) Db::exec('DELETE FROM admin_tokens WHERE token = ?', [$token]);
            throw new HttpException(401, 'Session expired, please log in again');
        }
        return $t;
    }

    public static function login(string $username, string $password): array
    {
        $u = Db::row('SELECT * FROM admin_users WHERE username = ?', [$username]);
        if (!$u || !password_verify($password, $u['password_hash'])) {
            usleep(300000); // slow down brute force a little
            throw new HttpException(401, 'Invalid username or password');
        }
        $token = bin2hex(random_bytes(32));
        $exp = time() + (int)Db::config('ADMIN_TOKEN_TTL', 43200);
        Db::exec('DELETE FROM admin_tokens WHERE expires_at < ?', [time()]);
        Db::exec('INSERT INTO admin_tokens (token, admin_id, expires_at) VALUES (?, ?, ?)', [$token, $u['id'], $exp]);
        return ['token' => $token, 'expires_at' => $exp, 'username' => $u['username']];
    }

    public static function logout(string $token): void
    {
        Db::exec('DELETE FROM admin_tokens WHERE token = ?', [$token]);
    }

    public static function createAdmin(string $username, string $password): void
    {
        $hash = password_hash($password, PASSWORD_DEFAULT);
        $existing = Db::row('SELECT id FROM admin_users WHERE username = ?', [$username]);
        if ($existing) {
            Db::exec('UPDATE admin_users SET password_hash = ? WHERE id = ?', [$hash, $existing['id']]);
        } else {
            Db::exec('INSERT INTO admin_users (username, password_hash, created_at) VALUES (?, ?, ?)', [$username, $hash, time()]);
        }
    }
}

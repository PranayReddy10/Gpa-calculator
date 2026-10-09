<?php
declare(strict_types=1);

namespace Gpa;

use PDO;

final class Db
{
    private static ?PDO $pdo = null;
    private static array $config = [];

    public static function init(array $config): void
    {
        self::$config = $config;
    }

    public static function config(string $key, $default = null)
    {
        return self::$config[$key] ?? $default;
    }

    public static function isSqlite(): bool
    {
        return (self::$config['DB_DRIVER'] ?? 'mysql') === 'sqlite';
    }

    public static function pdo(): PDO
    {
        if (self::$pdo === null) {
            $opts = [
                PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                PDO::ATTR_EMULATE_PREPARES => false,
            ];
            if (self::isSqlite()) {
                $path = self::$config['DB_SQLITE'] ?? (__DIR__ . '/../data/app.sqlite');
                self::$pdo = new PDO('sqlite:' . $path, null, null, $opts);
                self::$pdo->exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;');
            } else {
                $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
                    self::$config['DB_HOST'] ?? 'localhost', (int)(self::$config['DB_PORT'] ?? 3306), self::$config['DB_NAME'] ?? '');
                self::$pdo = new PDO($dsn, self::$config['DB_USER'] ?? '', self::$config['DB_PASS'] ?? '', $opts);
            }
        }
        return self::$pdo;
    }

    /** Runs schema.sql / schema.sqlite.sql (idempotent). */
    public static function migrate(): void
    {
        $file = self::isSqlite() ? __DIR__ . '/../schema.sqlite.sql' : __DIR__ . '/../schema.sql';
        $sql = file_get_contents($file);
        $sql = preg_replace('/^\s*--.*$/m', '', $sql);
        foreach (array_filter(array_map('trim', explode(';', $sql))) as $stmt) {
            self::pdo()->exec($stmt);
        }
    }

    public static function row(string $sql, array $params = []): ?array
    {
        $st = self::pdo()->prepare($sql);
        $st->execute($params);
        $r = $st->fetch();
        return $r === false ? null : $r;
    }

    public static function rows(string $sql, array $params = []): array
    {
        $st = self::pdo()->prepare($sql);
        $st->execute($params);
        return $st->fetchAll();
    }

    public static function value(string $sql, array $params = [])
    {
        $st = self::pdo()->prepare($sql);
        $st->execute($params);
        $v = $st->fetchColumn();
        return $v === false ? null : $v;
    }

    public static function exec(string $sql, array $params = []): int
    {
        $st = self::pdo()->prepare($sql);
        $st->execute($params);
        return $st->rowCount();
    }

    public static function insert(string $sql, array $params = []): int
    {
        self::exec($sql, $params);
        return (int)self::pdo()->lastInsertId();
    }
}

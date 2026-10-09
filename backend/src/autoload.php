<?php
declare(strict_types=1);

spl_autoload_register(static function (string $class): void {
    $prefix = 'Gpa\\';
    if (strncmp($class, $prefix, strlen($prefix)) !== 0) return;
    $rel = str_replace('\\', '/', substr($class, strlen($prefix)));
    $file = __DIR__ . '/' . $rel . '.php';
    if (is_file($file)) require $file;
});
// Request/Response/HttpException live together in Http.php
require_once __DIR__ . '/Http.php';

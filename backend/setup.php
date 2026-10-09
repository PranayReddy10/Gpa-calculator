<?php
/**
 * One-time installer: creates tables and the first admin user.
 * Usage (browser): https://example.com/gpa-api/setup.php?key=SETUP_KEY_FROM_CONFIG
 * Usage (CLI):     php setup.php
 * DELETE THIS FILE AFTER RUNNING IT.
 */
declare(strict_types=1);

require __DIR__ . '/src/autoload.php';
$configFile = __DIR__ . '/config.php';
if (!is_file($configFile)) exit("config.php missing — copy config.sample.php to config.php\n");
$config = require $configFile;

$isCli = PHP_SAPI === 'cli';
if (!$isCli) {
    header('Content-Type: text/plain');
    if (($config['SETUP_KEY'] ?? '') === '' || !hash_equals((string)$config['SETUP_KEY'], (string)($_GET['key'] ?? ''))) {
        http_response_code(403);
        exit("Forbidden: add ?key=SETUP_KEY (see config.php)\n");
    }
}

\Gpa\App::boot($config);
try {
    \Gpa\Db::migrate();
    echo "Tables created/verified.\n";
    \Gpa\Auth::createAdmin((string)$config['ADMIN_USER'], (string)$config['ADMIN_PASS']);
    echo "Admin user '{$config['ADMIN_USER']}' ready.\n";
    \Gpa\AppConfig::save(\Gpa\AppConfig::load());
    echo "Default app config stored.\n";
    echo "Done. Now delete setup.php and open admin/index.html\n";
} catch (Throwable $t) {
    http_response_code(500);
    echo 'Setup failed: ' . $t->getMessage() . "\n";
}

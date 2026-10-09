<?php
/**
 * GPA Calculator backend — single entry point.
 * Deploy this folder anywhere under an existing domain, e.g. https://example.com/gpa-api/
 * and point the app's API_BASE_URL at that URL.
 */
declare(strict_types=1);

require __DIR__ . '/src/autoload.php';

$configFile = __DIR__ . '/config.php';
if (!is_file($configFile)) {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode(['ok' => false, 'error' => 'config.php missing — copy config.sample.php to config.php']);
    exit;
}
$config = require $configFile;
\Gpa\App::boot($config);

// Base path = directory of this script as seen by the web server (works in sub-folders on shared hosting)
$basePath = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
$request = \Gpa\Request::fromGlobals($basePath);
$response = \Gpa\App::handle($request);
$response->send($config['ALLOWED_ORIGINS'] ?? []);

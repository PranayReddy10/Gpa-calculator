<?php
/**
 * Copy this file to config.php and fill in your values. config.php is never committed.
 */
return [
    // ---- Database (cPanel / shared hosting MySQL). Set DB_DRIVER to 'sqlite' only for local testing.
    'DB_DRIVER'   => 'mysql',
    'DB_HOST'     => 'localhost',
    'DB_PORT'     => 3306,
    'DB_NAME'     => 'your_db_name',
    'DB_USER'     => 'your_db_user',
    'DB_PASS'     => 'your_db_password',
    'DB_SQLITE'   => __DIR__ . '/data/app.sqlite',

    // ---- Shared secret sent by the Android app in the X-App-Key header (gradle property APP_API_KEY)
    'APP_API_KEY' => 'change-me-app-key',

    // ---- One-time installer protection: open setup.php?key=THIS_VALUE, then delete setup.php
    'SETUP_KEY'   => 'change-me-setup-key',

    // ---- First admin user created by setup.php
    'ADMIN_USER'  => 'admin',
    'ADMIN_PASS'  => 'change-me-admin-password',

    // ---- Admin session lifetime (seconds)
    'ADMIN_TOKEN_TTL' => 12 * 3600,

    // ---- If the admin page is hosted on another domain, list it here (CORS). Same-origin needs nothing.
    'ALLOWED_ORIGINS' => [],

    // ---- Retention: raw events older than this many days are purged opportunistically
    'EVENT_RETENTION_DAYS' => 90,
];

-- GPA Calculator backend schema (MySQL 5.7+ / MariaDB 10.3+)
CREATE TABLE IF NOT EXISTS app_config (
    id INT NOT NULL PRIMARY KEY,
    json LONGTEXT NOT NULL,
    updated_at INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_users (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_tokens (
    token VARCHAR(64) NOT NULL PRIMARY KEY,
    admin_id INT NOT NULL,
    expires_at INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    token VARCHAR(64) NOT NULL UNIQUE,
    manufacturer VARCHAR(64) NULL,
    model VARCHAR(96) NULL,
    os_version VARCHAR(32) NULL,
    sdk_int INT NULL,
    app_version VARCHAR(32) NULL,
    app_version_code INT NULL,
    language VARCHAR(16) NULL,
    country VARCHAR(8) NULL,
    timezone VARCHAR(64) NULL,
    ip VARCHAR(45) NULL,
    first_open INT NULL,
    first_seen INT NOT NULL,
    last_seen INT NOT NULL,
    open_count INT NOT NULL DEFAULT 0,
    INDEX idx_devices_first_seen (first_seen),
    INDEX idx_devices_last_seen (last_seen),
    INDEX idx_devices_country (country),
    INDEX idx_devices_version (app_version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device_days (
    device_id VARCHAR(64) NOT NULL,
    day CHAR(10) NOT NULL,
    PRIMARY KEY (device_id, day),
    INDEX idx_device_days_day (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    params TEXT NULL,
    app_version_code INT NULL,
    ts INT NOT NULL,
    INDEX idx_events_name_ts (name, ts),
    INDEX idx_events_device (device_id),
    INDEX idx_events_ts (ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS saved_calculations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    local_id BIGINT NULL,
    calc_id VARCHAR(64) NOT NULL,
    title VARCHAR(128) NULL,
    inputs TEXT NULL,
    results TEXT NULL,
    summary VARCHAR(255) NULL,
    created_at INT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_saves_device (device_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS push_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NULL,
    message TEXT NOT NULL,
    target VARCHAR(128) NOT NULL,
    payload TEXT NULL,
    onesignal_id VARCHAR(64) NULL,
    recipients INT NULL,
    status VARCHAR(16) NOT NULL,
    error TEXT NULL,
    admin VARCHAR(64) NULL,
    created_at INT NOT NULL,
    INDEX idx_push_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

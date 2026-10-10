-- Same schema for SQLite (local testing only)
CREATE TABLE IF NOT EXISTS app_config (id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS admin_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS admin_tokens (token TEXT NOT NULL PRIMARY KEY, admin_id INTEGER NOT NULL, expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS devices (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL UNIQUE, token TEXT NOT NULL UNIQUE, manufacturer TEXT, model TEXT, os_version TEXT, sdk_int INTEGER, app_version TEXT, app_version_code INTEGER, language TEXT, country TEXT, timezone TEXT, ip TEXT, first_open INTEGER, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, open_count INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS idx_devices_first_seen ON devices(first_seen);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen);
CREATE TABLE IF NOT EXISTS device_days (device_id TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY (device_id, day));
CREATE INDEX IF NOT EXISTS idx_device_days_day ON device_days(day);
CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL, name TEXT NOT NULL, params TEXT, app_version_code INTEGER, ts INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_events_name_ts ON events(name, ts);
CREATE INDEX IF NOT EXISTS idx_events_device ON events(device_id);
CREATE TABLE IF NOT EXISTS saved_calculations (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL, local_id INTEGER, calc_id TEXT NOT NULL, title TEXT, inputs TEXT, results TEXT, summary TEXT, created_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS idx_saves_device ON saved_calculations(device_id, deleted, created_at);
CREATE TABLE IF NOT EXISTS push_log (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, message TEXT NOT NULL, target TEXT NOT NULL, payload TEXT, onesignal_id TEXT, recipients INTEGER, status TEXT NOT NULL, error TEXT, admin TEXT, created_at INTEGER NOT NULL);

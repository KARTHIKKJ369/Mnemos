CREATE TABLE schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    device_type TEXT NOT NULL,
    auth_token_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE TABLE files (
    id TEXT PRIMARY KEY,
    hash TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),
    uploaded_by_device_id TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    thumbnail_path TEXT,
    preview_path TEXT,
    captured_at INTEGER,
    uploaded_at INTEGER NOT NULL,
    is_locked INTEGER NOT NULL DEFAULT 0 CHECK (is_locked IN (0, 1)),
    status TEXT NOT NULL CHECK (status IN ('uploading', 'processing', 'ready', 'failed')),
    FOREIGN KEY (uploaded_by_device_id) REFERENCES devices(id)
);

CREATE TABLE file_sync_state (
    device_id TEXT NOT NULL,
    file_id TEXT NOT NULL,
    synced_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, file_id),
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE INDEX idx_files_uploaded_at ON files(uploaded_at);
CREATE INDEX idx_files_unlocked_uploaded_at ON files(is_locked, uploaded_at);
CREATE INDEX idx_file_sync_state_file_id ON file_sync_state(file_id);

CREATE TABLE media_index (
    file_id TEXT PRIMARY KEY,
    filename TEXT NOT NULL,
    filename_normalized TEXT NOT NULL,
    extension TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    width INTEGER,
    height INTEGER,
    duration_ms INTEGER,
    taken_at INTEGER,
    uploaded_at INTEGER NOT NULL,
    camera_make TEXT,
    camera_model TEXT,
    gps_lat REAL,
    gps_lon REAL,
    favorite INTEGER NOT NULL DEFAULT 0 CHECK (favorite IN (0, 1)),
    deleted INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
    thumbnail_available INTEGER NOT NULL DEFAULT 0 CHECK (thumbnail_available IN (0, 1)),
    preview_available INTEGER NOT NULL DEFAULT 0 CHECK (preview_available IN (0, 1)),
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);
CREATE INDEX idx_media_index_filename_normalized ON media_index(filename_normalized);
CREATE INDEX idx_media_index_taken_at ON media_index(taken_at);
CREATE INDEX idx_media_index_mime_type ON media_index(mime_type);
CREATE INDEX idx_media_index_uploaded_at ON media_index(uploaded_at);

CREATE TABLE media_index_jobs (
    file_id TEXT PRIMARY KEY,
    state TEXT NOT NULL CHECK (state IN ('pending', 'processing', 'done')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER NOT NULL,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);
CREATE INDEX idx_media_index_jobs_pending ON media_index_jobs(state, next_attempt_at);

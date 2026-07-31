CREATE TABLE media_processing_jobs (
    file_id TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('pending', 'processing', 'done')),
    last_error TEXT,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

CREATE INDEX idx_media_processing_jobs_pending ON media_processing_jobs(state, next_attempt_at);

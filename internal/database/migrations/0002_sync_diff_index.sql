CREATE INDEX idx_files_sync_diff ON files(is_locked, status, uploaded_at, id);

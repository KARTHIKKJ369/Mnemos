CREATE TABLE encrypted_uploads (
    id TEXT PRIMARY KEY,
    vault_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('open', 'complete')),
    created_at INTEGER NOT NULL,
    FOREIGN KEY (vault_id) REFERENCES vaults(id), FOREIGN KEY (device_id) REFERENCES devices(id)
);
CREATE TABLE encrypted_upload_chunks (
    upload_id TEXT NOT NULL, chunk_index INTEGER NOT NULL, size_bytes INTEGER NOT NULL,
    storage_path TEXT NOT NULL, nonce BLOB NOT NULL, tag BLOB NOT NULL,
    PRIMARY KEY (upload_id, chunk_index), FOREIGN KEY (upload_id) REFERENCES encrypted_uploads(id) ON DELETE CASCADE
);
CREATE TABLE encrypted_object_chunks (
    object_id TEXT NOT NULL, chunk_index INTEGER NOT NULL, size_bytes INTEGER NOT NULL, storage_path TEXT NOT NULL,
    PRIMARY KEY (object_id, chunk_index), FOREIGN KEY (object_id) REFERENCES encrypted_objects(id) ON DELETE CASCADE
);

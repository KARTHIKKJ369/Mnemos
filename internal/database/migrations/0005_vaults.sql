CREATE TABLE vaults (
    id TEXT PRIMARY KEY,
    vault_type TEXT NOT NULL CHECK (vault_type IN ('legacy', 'encrypted')),
    salt BLOB,
    argon2_time INTEGER,
    argon2_memory_kib INTEGER,
    argon2_threads INTEGER,
    algorithm_version INTEGER,
    created_at INTEGER NOT NULL
);
CREATE TABLE vault_members (
    vault_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (vault_id, device_id),
    FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);
CREATE TABLE encrypted_objects (
    id TEXT PRIMARY KEY,
    vault_id TEXT NOT NULL,
    ciphertext_hash TEXT NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),
    storage_path TEXT NOT NULL,
    chunk_size INTEGER NOT NULL,
    chunk_count INTEGER NOT NULL,
    base_nonce BLOB NOT NULL,
    encrypted_file_key BLOB NOT NULL,
    encrypted_manifest BLOB NOT NULL,
    algorithm_version INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE,
    UNIQUE(vault_id, ciphertext_hash)
);
CREATE INDEX idx_encrypted_objects_vault_created ON encrypted_objects(vault_id, created_at);

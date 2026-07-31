package database

import (
	"context"
	"path/filepath"
	"testing"
)

func TestOpenAppliesInitialMigration(t *testing.T) {
	t.Parallel()

	databasePath := filepath.Join(t.TempDir(), "vault.db")
	db, err := Open(context.Background(), databasePath)
	if err != nil {
		t.Fatalf("Open() error = %v", err)
	}
	defer db.Close()

	var count int
	if err := db.QueryRow("SELECT COUNT(*) FROM schema_migrations WHERE version = '0001_initial'").Scan(&count); err != nil {
		t.Fatalf("query migration record: %v", err)
	}
	if count != 1 {
		t.Fatalf("migration record count = %d, want 1", count)
	}

	if _, err := db.Exec("INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status) VALUES ('file', 'hash', 'file.jpg', 'image/jpeg', 1, 'missing', 'blobs/by-device/test/2026/07/hash.jpg', 0, 'ready')"); err == nil {
		t.Fatal("foreign key constraint was not enforced")
	}
}

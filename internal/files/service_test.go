package files

import (
	"context"
	"database/sql"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"photovault/internal/database"
)

func TestServiceCachesExistingHash(t *testing.T) {
	db, repository := newTestRepository(t)
	insertTestFile(t, db, "file-1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
	service := NewService(repository, NewLRUExistenceCache(10, time.Minute))

	first, exists, err := service.Exists(context.Background(), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
	if err != nil || !exists || first.FileID != "file-1" {
		t.Fatalf("first lookup = %+v, exists=%t, err=%v", first, exists, err)
	}
	if _, err := db.Exec("DELETE FROM files WHERE id = ?", "file-1"); err != nil {
		t.Fatalf("delete source row: %v", err)
	}
	second, exists, err := service.Exists(context.Background(), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
	if err != nil || !exists || second.FileID != "file-1" {
		t.Fatalf("cached lookup = %+v, exists=%t, err=%v", second, exists, err)
	}
}

func TestLRUExistenceCacheExpiresEntries(t *testing.T) {
	cache := NewLRUExistenceCache(1, time.Minute)
	now := time.Date(2026, time.July, 31, 0, 0, 0, 0, time.UTC)
	cache.now = func() time.Time { return now }
	cache.Set("hash", Existence{FileID: "file", SizeBytes: 1})
	if _, ok := cache.Get("hash"); !ok {
		t.Fatal("cache entry was missing before expiration")
	}
	now = now.Add(time.Minute)
	if _, ok := cache.Get("hash"); ok {
		t.Fatal("cache entry was returned after expiration")
	}
}

func TestRepositoryUsesHashIndex(t *testing.T) {
	db, _ := newTestRepository(t)
	rows, err := db.Query("EXPLAIN QUERY PLAN SELECT id, size_bytes FROM files WHERE hash = ?", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
	if err != nil {
		t.Fatalf("explain query plan: %v", err)
	}
	defer rows.Close()
	var detail string
	for rows.Next() {
		var id, parent, ignored int
		if err := rows.Scan(&id, &parent, &ignored, &detail); err != nil {
			t.Fatalf("scan query plan: %v", err)
		}
	}
	if err := rows.Err(); err != nil {
		t.Fatalf("iterate query plan: %v", err)
	}
	if detail == "" || !containsIndexUsage(detail) {
		t.Fatalf("query plan = %q, want indexed hash lookup", detail)
	}
}

func newTestRepository(t *testing.T) (*sql.DB, *Repository) {
	t.Helper()
	db, err := database.Open(context.Background(), filepath.Join(t.TempDir(), "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	if _, err := db.Exec("INSERT INTO devices (id, name, device_type, auth_token_hash, created_at, last_seen_at) VALUES ('device-1', 'Device', 'ios', 'token', 0, 0)"); err != nil {
		t.Fatalf("insert device: %v", err)
	}
	return db, NewRepository(db)
}

func insertTestFile(t *testing.T, db *sql.DB, id, hash string) {
	t.Helper()
	if _, err := db.Exec("INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status) VALUES (?, ?, 'file.bin', 'application/octet-stream', 1, 'device-1', 'blobs/test', 0, 'ready')", id, hash); err != nil {
		t.Fatalf("insert file: %v", err)
	}
}

func containsIndexUsage(detail string) bool {
	return strings.Contains(detail, "USING INDEX") || strings.Contains(detail, "USING COVERING INDEX")
}

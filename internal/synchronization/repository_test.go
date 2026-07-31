package synchronization

import (
	"context"
	"database/sql"
	"path/filepath"
	"testing"

	"photovault/internal/database"
)

func TestSyncRepositoryDiffExcludesUploaderAndLockedFiles(t *testing.T) {
	t.Parallel()

	ctx := context.Background()
	db := openSyncTestDatabase(t)
	repository := newSyncTestRepository(t, ctx, db)
	insertDevice(t, db, "device-a", "Device A")
	insertDevice(t, db, "device-b", "Device B")
	insertFile(t, db, "00000000-0000-0000-0000-000000000001", "hash-a", "device-a", 100, 0, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-000000000002", "hash-b", "device-b", 200, 0, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-000000000003", "hash-locked", "device-a", 300, 1, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-000000000004", "hash-processing", "device-a", 400, 0, "processing")
	if _, err := db.Exec("INSERT INTO file_sync_state (device_id, file_id, synced_at) VALUES ('device-a', '00000000-0000-0000-0000-000000000001', 100)"); err != nil {
		t.Fatalf("insert sync state: %v", err)
	}
	if _, err := db.Exec("INSERT INTO file_sync_state (device_id, file_id, synced_at) VALUES ('device-b', '00000000-0000-0000-0000-000000000002', 200)"); err != nil {
		t.Fatalf("insert sync state: %v", err)
	}

	files, err := repository.Diff(ctx, "device-a", nil, 10)
	if err != nil {
		t.Fatalf("diff for device-a: %v", err)
	}
	if len(files) != 1 || files[0].FileID != "00000000-0000-0000-0000-000000000002" {
		t.Fatalf("device-a diff = %+v, want only file-b", files)
	}

	files, err = repository.Diff(ctx, "device-b", nil, 10)
	if err != nil {
		t.Fatalf("diff for device-b: %v", err)
	}
	if len(files) != 1 || files[0].FileID != "00000000-0000-0000-0000-000000000001" {
		t.Fatalf("device-b diff = %+v, want only file-a", files)
	}
}

func TestSyncRepositoryDiffUsesSinceCursor(t *testing.T) {
	t.Parallel()

	ctx := context.Background()
	db := openSyncTestDatabase(t)
	repository := newSyncTestRepository(t, ctx, db)
	insertDevice(t, db, "device-a", "Device A")
	insertDevice(t, db, "device-b", "Device B")
	insertFile(t, db, "00000000-0000-0000-0000-000000000001", "hash-a", "device-a", 100, 0, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-000000000002", "hash-b", "device-b", 200, 0, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-000000000003", "hash-c", "device-a", 300, 0, "ready")
	if _, err := db.Exec("INSERT INTO file_sync_state (device_id, file_id, synced_at) VALUES ('device-b', '00000000-0000-0000-0000-000000000002', 200)"); err != nil {
		t.Fatalf("insert sync state: %v", err)
	}

	since := int64(150)
	files, err := repository.Diff(ctx, "device-b", &since, 10)
	if err != nil {
		t.Fatalf("diff with since: %v", err)
	}
	if len(files) != 1 || files[0].FileID != "00000000-0000-0000-0000-000000000003" {
		t.Fatalf("since diff = %+v, want file-c", files)
	}
}

func TestSyncRepositoryDiffOrdersDeterministically(t *testing.T) {
	t.Parallel()

	ctx := context.Background()
	db := openSyncTestDatabase(t)
	repository := newSyncTestRepository(t, ctx, db)
	insertDevice(t, db, "device-a", "Device A")
	insertDevice(t, db, "device-b", "Device B")
	insertFile(t, db, "00000000-0000-0000-0000-00000000000a", "hash-z", "device-a", 100, 0, "ready")
	insertFile(t, db, "00000000-0000-0000-0000-00000000000b", "hash-y", "device-a", 100, 0, "ready")

	files, err := repository.Diff(ctx, "device-b", nil, 10)
	if err != nil {
		t.Fatalf("diff: %v", err)
	}
	if len(files) != 2 {
		t.Fatalf("file count = %d, want 2", len(files))
	}
	if files[0].FileID != "00000000-0000-0000-0000-00000000000a" || files[1].FileID != "00000000-0000-0000-0000-00000000000b" {
		t.Fatalf("order = [%s, %s], want [00a, 00b]", files[0].FileID, files[1].FileID)
	}
}

func TestSyncRepositoryAckIsIdempotent(t *testing.T) {
	t.Parallel()

	ctx := context.Background()
	db := openSyncTestDatabase(t)
	repository := newSyncTestRepository(t, ctx, db)
	insertDevice(t, db, "device-a", "Device A")
	insertFile(t, db, "00000000-0000-0000-0000-000000000001", "hash-a", "device-a", 100, 0, "ready")

	if err := repository.Ack(ctx, "device-a", []string{"00000000-0000-0000-0000-000000000001"}, 500); err != nil {
		t.Fatalf("first ack: %v", err)
	}
	if err := repository.Ack(ctx, "device-a", []string{"00000000-0000-0000-0000-000000000001"}, 600); err != nil {
		t.Fatalf("second ack: %v", err)
	}
	var count int
	if err := db.QueryRow("SELECT COUNT(*) FROM file_sync_state WHERE device_id = 'device-a' AND file_id = '00000000-0000-0000-0000-000000000001'").Scan(&count); err != nil {
		t.Fatalf("count sync rows: %v", err)
	}
	if count != 1 {
		t.Fatalf("sync row count = %d, want 1", count)
	}
}

func TestSyncRepositoryAckRejectsUnknownFileID(t *testing.T) {
	t.Parallel()

	ctx := context.Background()
	db := openSyncTestDatabase(t)
	repository := newSyncTestRepository(t, ctx, db)
	insertDevice(t, db, "device-a", "Device A")

	err := repository.Ack(ctx, "device-a", []string{"00000000-0000-0000-0000-000000000001"}, 100)
	if err != ErrUnknownFileID {
		t.Fatalf("error = %v, want ErrUnknownFileID", err)
	}
}

func openSyncTestDatabase(t *testing.T) *sql.DB {
	t.Helper()
	db, err := database.Open(context.Background(), filepath.Join(t.TempDir(), "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	return db
}

func newSyncTestRepository(t *testing.T, ctx context.Context, db *sql.DB) *SyncRepository {
	t.Helper()
	repository, err := NewSyncRepository(ctx, db)
	if err != nil {
		t.Fatalf("create sync repository: %v", err)
	}
	t.Cleanup(func() { repository.Close() })
	return repository
}

func insertDevice(t *testing.T, db *sql.DB, id, name string) {
	t.Helper()
	if _, err := db.Exec("INSERT INTO devices (id, name, device_type, auth_token_hash, created_at, last_seen_at) VALUES (?, ?, 'ios', 'hash', 0, 0)", id, name); err != nil {
		t.Fatalf("insert device: %v", err)
	}
}

func insertFile(t *testing.T, db *sql.DB, id, hash, deviceID string, uploadedAt int64, locked int, status string) {
	t.Helper()
	if _, err := db.Exec(`
		INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, is_locked, status)
		VALUES (?, ?, ?, 'image/jpeg', 1, ?, 'blobs/test', ?, ?, ?)
	`, id, hash, id+".jpg", deviceID, uploadedAt, locked, status); err != nil {
		t.Fatalf("insert file: %v", err)
	}
}

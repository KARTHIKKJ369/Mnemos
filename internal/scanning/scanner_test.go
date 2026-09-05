package scanning

import (
	"context"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"photovault/internal/database"
	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/storage"
)

type mockJobEnqueuer struct {
	enqueued []string
}

func (m *mockJobEnqueuer) Enqueue(ctx context.Context, fileID string) error {
	m.enqueued = append(m.enqueued, fileID)
	return nil
}

func TestFolderScanner(t *testing.T) {
	storageRoot := t.TempDir()
	db, err := database.Open(context.Background(), filepath.Join(storageRoot, "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	defer db.Close()

	adminDevice := devices.Device{
		ID:         "admin-device",
		Name:       "Server Admin",
		DeviceType: "mac",
	}
	if _, err := db.Exec("INSERT INTO devices (id, name, device_type, auth_token_hash, created_at, last_seen_at) VALUES (?, ?, ?, 'hash', 0, 0)",
		adminDevice.ID, adminDevice.Name, adminDevice.DeviceType); err != nil {
		t.Fatalf("insert device: %v", err)
	}

	layout, err := storage.Ensure(storageRoot)
	if err != nil {
		t.Fatalf("prepare layout: %v", err)
	}
	blobStore := storage.NewBlobStore(layout)
	fileRepo := files.NewRepository(db)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	mockJob := &mockJobEnqueuer{}

	scanner := NewFolderScanner(blobStore, fileRepo, storageRoot, logger, mockJob)

	// Create a test folder with some media files and non-media files
	photosDir := filepath.Join(t.TempDir(), "MyPhotos")
	if err := os.MkdirAll(photosDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}

	photo1 := filepath.Join(photosDir, "beach.jpg")
	if err := os.WriteFile(photo1, []byte("\xFF\xD8\xFF\xE0\x00\x10JFIFfake-image-1"), 0o644); err != nil {
		t.Fatalf("write photo1: %v", err)
	}

	photo2 := filepath.Join(photosDir, "sunset.png")
	if err := os.WriteFile(photo2, []byte("\x89PNG\r\n\x1a\nfake-image-2"), 0o644); err != nil {
		t.Fatalf("write photo2: %v", err)
	}

	notes := filepath.Join(photosDir, "notes.txt")
	if err := os.WriteFile(notes, []byte("some text notes"), 0o644); err != nil {
		t.Fatalf("write notes: %v", err)
	}

	// First scan: should scan 2 images, import 2, skip notes.txt
	result, err := scanner.Scan(context.Background(), photosDir, adminDevice)
	if err != nil {
		t.Fatalf("scan failed: %v", err)
	}

	if result.Scanned != 2 {
		t.Errorf("expected Scanned=2, got %d", result.Scanned)
	}
	if result.Imported != 2 {
		t.Errorf("expected Imported=2, got %d", result.Imported)
	}
	if result.AlreadyIndexed != 0 {
		t.Errorf("expected AlreadyIndexed=0, got %d", result.AlreadyIndexed)
	}
	if len(mockJob.enqueued) != 2 {
		t.Errorf("expected 2 enqueued jobs, got %d", len(mockJob.enqueued))
	}

	// Second scan of the same folder: should detect both as already indexed
	result2, err := scanner.Scan(context.Background(), photosDir, adminDevice)
	if err != nil {
		t.Fatalf("second scan failed: %v", err)
	}

	if result2.Scanned != 2 {
		t.Errorf("expected Scanned=2, got %d", result2.Scanned)
	}
	if result2.Imported != 0 {
		t.Errorf("expected Imported=0, got %d", result2.Imported)
	}
	if result2.AlreadyIndexed != 2 {
		t.Errorf("expected AlreadyIndexed=2, got %d", result2.AlreadyIndexed)
	}
}

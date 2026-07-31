// Package files manages PhotoVault media metadata and sync state.
package files

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
)

// File contains stored media metadata needed by upload responses and later API operations.
type File struct {
	ID        string
	Hash      string
	SizeBytes int64
	MIMEType  string
	Status    string
}

// CreateInput is the metadata recorded for a newly uploaded blob.
type CreateInput struct {
	Hash               string
	OriginalFilename   string
	MIMEType           string
	SizeBytes          int64
	UploadedByDeviceID string
	StoragePath        string
	UploadedAt         time.Time
}

// Store writes and reads PhotoVault file metadata.
type Store struct {
	database *sql.DB
}

// NewStore constructs a SQLite-backed file metadata store.
func NewStore(database *sql.DB) *Store {
	return &Store{database: database}
}

// CreateOrGet creates a file record and uploader sync state, or returns the existing hash record.
func (store *Store) CreateOrGet(ctx context.Context, input CreateInput) (File, bool, error) {
	tx, err := store.database.BeginTx(ctx, nil)
	if err != nil {
		return File{}, false, fmt.Errorf("begin file transaction: %w", err)
	}
	defer tx.Rollback()

	file := File{ID: uuid.NewString(), Hash: input.Hash, SizeBytes: input.SizeBytes, MIMEType: input.MIMEType, Status: "ready"}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, file.ID, file.Hash, input.OriginalFilename, file.MIMEType, file.SizeBytes, input.UploadedByDeviceID, input.StoragePath, input.UploadedAt.UnixMilli(), file.Status)
	created := err == nil
	if err != nil {
		if !isUniqueConstraint(err) {
			return File{}, false, fmt.Errorf("insert file: %w", err)
		}
		file, err = findByHash(ctx, tx, input.Hash)
		if err != nil {
			return File{}, false, err
		}
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO file_sync_state (device_id, file_id, synced_at)
		VALUES (?, ?, ?)
		ON CONFLICT(device_id, file_id) DO NOTHING
	`, input.UploadedByDeviceID, file.ID, input.UploadedAt.UnixMilli()); err != nil {
		return File{}, false, fmt.Errorf("insert file sync state: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return File{}, false, fmt.Errorf("commit file transaction: %w", err)
	}
	return file, created, nil
}

func findByHash(ctx context.Context, tx *sql.Tx, hash string) (File, error) {
	var file File
	err := tx.QueryRowContext(ctx, "SELECT id, hash, size_bytes, mime_type, status FROM files WHERE hash = ?", hash).Scan(&file.ID, &file.Hash, &file.SizeBytes, &file.MIMEType, &file.Status)
	if errors.Is(err, sql.ErrNoRows) {
		return File{}, fmt.Errorf("find concurrent file: %w", err)
	}
	if err != nil {
		return File{}, fmt.Errorf("select file by hash: %w", err)
	}
	return file, nil
}

func isUniqueConstraint(err error) bool {
	return strings.Contains(err.Error(), "UNIQUE constraint failed: files.hash")
}

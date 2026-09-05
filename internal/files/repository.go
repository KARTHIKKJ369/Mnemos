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

// Repository persists and retrieves PhotoVault file metadata.
type Repository struct {
	database *sql.DB
}

// NewRepository constructs a SQLite-backed file metadata repository.
func NewRepository(database *sql.DB) *Repository {
	return &Repository{database: database}
}

// CreateOrGet creates a file record and uploader sync state, or returns the existing hash record.
func (repository *Repository) CreateOrGet(ctx context.Context, input CreateInput) (File, bool, error) {
	tx, err := repository.database.BeginTx(ctx, nil)
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

// FindExistence performs one indexed lookup for a file hash.
func (repository *Repository) FindExistence(ctx context.Context, hash string) (Existence, bool, error) {
	var result Existence
	err := repository.database.QueryRowContext(ctx, "SELECT id, size_bytes FROM files WHERE hash = ?", hash).Scan(&result.FileID, &result.SizeBytes)
	if errors.Is(err, sql.ErrNoRows) {
		return Existence{}, false, nil
	}
	if err != nil {
		return Existence{}, false, fmt.Errorf("query file existence: %w", err)
	}
	return result, true, nil
}

// GetFileByID returns the media metadata required to retrieve an original blob.
func (repository *Repository) GetFileByID(ctx context.Context, id string) (File, bool, error) {
	var file File
	var uploadedAtMillis int64
	err := repository.database.QueryRowContext(ctx, `
		SELECT id, hash, original_filename, mime_type, size_bytes, storage_path, COALESCE(thumbnail_path, ''), COALESCE(preview_path, ''), uploaded_at, status
		FROM files
		WHERE id = ?
	`, id).Scan(&file.ID, &file.Hash, &file.OriginalFilename, &file.MIMEType, &file.SizeBytes, &file.StoragePath, &file.ThumbnailPath, &file.PreviewPath, &uploadedAtMillis, &file.Status)
	if errors.Is(err, sql.ErrNoRows) {
		return File{}, false, nil
	}
	if err != nil {
		return File{}, false, fmt.Errorf("query file by ID: %w", err)
	}
	file.UploadedAt = time.UnixMilli(uploadedAtMillis).UTC()
	return file, true, nil
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

// ReassignDeviceFiles updates the uploaded_by_device_id of all files from one device to another.
func (r *Repository) ReassignDeviceFiles(ctx context.Context, fromDeviceID, toDeviceID string) error {
	_, err := r.database.ExecContext(ctx, "UPDATE files SET uploaded_by_device_id = ? WHERE uploaded_by_device_id = ?", toDeviceID, fromDeviceID)
	if err != nil {
		return fmt.Errorf("reassign device files: %w", err)
	}
	return nil
}

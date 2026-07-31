// Package processing persists and runs asynchronous derived-media jobs.
package processing

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"photovault/internal/files"
)

type Repository struct{ database *sql.DB }

func NewRepository(database *sql.DB) *Repository { return &Repository{database: database} }

// Enqueue records idempotent work for a successfully stored original.
func (r *Repository) Enqueue(ctx context.Context, fileID string) error {
	_, err := r.database.ExecContext(ctx, `INSERT INTO media_processing_jobs (file_id, next_attempt_at, state) VALUES (?, ?, 'pending') ON CONFLICT(file_id) DO UPDATE SET state = CASE WHEN state = 'done' THEN 'done' ELSE 'pending' END`, fileID, time.Now().UnixMilli())
	if err != nil {
		return fmt.Errorf("enqueue media job: %w", err)
	}
	return nil
}

func (r *Repository) Recover(ctx context.Context) error {
	_, err := r.database.ExecContext(ctx, "UPDATE media_processing_jobs SET state = 'pending' WHERE state = 'processing'")
	if err != nil {
		return fmt.Errorf("recover media jobs: %w", err)
	}
	return nil
}

func (r *Repository) Claim(ctx context.Context, now time.Time) (files.File, bool, error) {
	tx, err := r.database.BeginTx(ctx, nil)
	if err != nil {
		return files.File{}, false, fmt.Errorf("begin claim job: %w", err)
	}
	defer tx.Rollback()
	var id string
	err = tx.QueryRowContext(ctx, "SELECT file_id FROM media_processing_jobs WHERE state = 'pending' AND next_attempt_at <= ? ORDER BY next_attempt_at, file_id LIMIT 1", now.UnixMilli()).Scan(&id)
	if err == sql.ErrNoRows {
		return files.File{}, false, nil
	}
	if err != nil {
		return files.File{}, false, fmt.Errorf("select media job: %w", err)
	}
	if _, err := tx.ExecContext(ctx, "UPDATE media_processing_jobs SET state = 'processing', attempts = attempts + 1 WHERE file_id = ?", id); err != nil {
		return files.File{}, false, fmt.Errorf("claim media job: %w", err)
	}
	var f files.File
	var uploaded int64
	err = tx.QueryRowContext(ctx, `SELECT id, hash, original_filename, mime_type, size_bytes, storage_path, COALESCE(thumbnail_path, ''), COALESCE(preview_path, ''), uploaded_at, status FROM files WHERE id = ?`, id).Scan(&f.ID, &f.Hash, &f.OriginalFilename, &f.MIMEType, &f.SizeBytes, &f.StoragePath, &f.ThumbnailPath, &f.PreviewPath, &uploaded, &f.Status)
	if err != nil {
		return files.File{}, false, fmt.Errorf("load media job file: %w", err)
	}
	f.UploadedAt = time.UnixMilli(uploaded).UTC()
	if err := tx.Commit(); err != nil {
		return files.File{}, false, fmt.Errorf("commit claim job: %w", err)
	}
	return f, true, nil
}

func (r *Repository) Complete(ctx context.Context, fileID, thumbnailPath, previewPath string) error {
	tx, err := r.database.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin complete media job: %w", err)
	}
	defer tx.Rollback()
	if thumbnailPath != "" {
		if _, err := tx.ExecContext(ctx, "UPDATE files SET thumbnail_path = ? WHERE id = ?", thumbnailPath, fileID); err != nil {
			return fmt.Errorf("update thumbnail path: %w", err)
		}
	}
	if previewPath != "" {
		if _, err := tx.ExecContext(ctx, "UPDATE files SET preview_path = ? WHERE id = ?", previewPath, fileID); err != nil {
			return fmt.Errorf("update preview path: %w", err)
		}
	}
	if _, err := tx.ExecContext(ctx, "UPDATE media_processing_jobs SET state = 'done', last_error = NULL WHERE file_id = ?", fileID); err != nil {
		return fmt.Errorf("complete media job: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit media job: %w", err)
	}
	return nil
}

func (r *Repository) Retry(ctx context.Context, fileID string, delay time.Duration, cause error) error {
	_, err := r.database.ExecContext(ctx, "UPDATE media_processing_jobs SET state = 'pending', next_attempt_at = ?, last_error = ? WHERE file_id = ?", time.Now().Add(delay).UnixMilli(), cause.Error(), fileID)
	if err != nil {
		return fmt.Errorf("retry media job: %w", err)
	}
	return nil
}

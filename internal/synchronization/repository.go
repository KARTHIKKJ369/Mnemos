package synchronization

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
)

const diffWithoutSinceQuery = `
SELECT f.id, f.hash, f.original_filename, f.mime_type, f.size_bytes,
       f.thumbnail_path IS NOT NULL, f.preview_path IS NOT NULL, f.uploaded_at
FROM files f
WHERE f.is_locked = 0
  AND f.status = 'ready'
  AND NOT EXISTS (SELECT 1 FROM file_sync_state s WHERE s.device_id = ? AND s.file_id = f.id)
ORDER BY f.uploaded_at ASC, f.id ASC
LIMIT ?`

const diffWithSinceQuery = `
SELECT f.id, f.hash, f.original_filename, f.mime_type, f.size_bytes,
       f.thumbnail_path IS NOT NULL, f.preview_path IS NOT NULL, f.uploaded_at
FROM files f
WHERE f.is_locked = 0
  AND f.status = 'ready'
  AND f.uploaded_at > ?
  AND NOT EXISTS (SELECT 1 FROM file_sync_state s WHERE s.device_id = ? AND s.file_id = f.id)
ORDER BY f.uploaded_at ASC, f.id ASC
LIMIT ?`

const ackInsertQuery = `
INSERT INTO file_sync_state (device_id, file_id, synced_at)
VALUES (?, ?, ?)
ON CONFLICT DO NOTHING`

// SyncRepository executes optimized sync queries.
type SyncRepository struct {
	database     *sql.DB
	withoutSince *sql.Stmt
	withSince    *sql.Stmt
	ack          *sql.Stmt
}

// NewSyncRepository prepares the indexed sync statements.
func NewSyncRepository(ctx context.Context, database *sql.DB) (*SyncRepository, error) {
	withoutSince, err := database.PrepareContext(ctx, diffWithoutSinceQuery)
	if err != nil {
		return nil, fmt.Errorf("prepare sync-diff query without since: %w", err)
	}
	withSince, err := database.PrepareContext(ctx, diffWithSinceQuery)
	if err != nil {
		withoutSince.Close()
		return nil, fmt.Errorf("prepare sync-diff query with since: %w", err)
	}
	ack, err := database.PrepareContext(ctx, ackInsertQuery)
	if err != nil {
		withoutSince.Close()
		withSince.Close()
		return nil, fmt.Errorf("prepare sync ack statement: %w", err)
	}
	return &SyncRepository{database: database, withoutSince: withoutSince, withSince: withSince, ack: ack}, nil
}

// Close releases the prepared sync statements.
func (repository *SyncRepository) Close() error {
	withoutSinceErr := repository.withoutSince.Close()
	withSinceErr := repository.withSince.Close()
	ackErr := repository.ack.Close()
	if withoutSinceErr != nil {
		return fmt.Errorf("close sync-diff statement without since: %w", withoutSinceErr)
	}
	if withSinceErr != nil {
		return fmt.Errorf("close sync-diff statement with since: %w", withSinceErr)
	}
	if ackErr != nil {
		return fmt.Errorf("close sync ack statement: %w", ackErr)
	}
	return nil
}

// Diff returns unsynchronized ready, unlocked files using exactly one query.
func (repository *SyncRepository) Diff(ctx context.Context, deviceID string, since *int64, limit int) ([]File, error) {
	var rows *sql.Rows
	var err error
	if since == nil {
		rows, err = repository.withoutSince.QueryContext(ctx, deviceID, limit)
	} else {
		rows, err = repository.withSince.QueryContext(ctx, *since, deviceID, limit)
	}
	if err != nil {
		return nil, fmt.Errorf("execute sync-diff query: %w", err)
	}
	defer rows.Close()

	files := make([]File, 0, limit)
	for rows.Next() {
		var file File
		if err := rows.Scan(&file.FileID, &file.Hash, &file.Filename, &file.MIMEType, &file.SizeBytes, &file.ThumbnailAvailable, &file.PreviewAvailable, &file.UploadedAt); err != nil {
			return nil, fmt.Errorf("scan sync-diff row: %w", err)
		}
		files = append(files, file)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate sync-diff rows: %w", err)
	}
	return files, nil
}

// Ack inserts sync-state rows for one device in a single transaction.
func (repository *SyncRepository) Ack(ctx context.Context, deviceID string, fileIDs []string, syncedAt int64) error {
	if err := repository.ensureFilesExist(ctx, fileIDs); err != nil {
		return err
	}
	tx, err := repository.database.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin sync ack transaction: %w", err)
	}
	defer tx.Rollback()

	statement := tx.StmtContext(ctx, repository.ack)
	defer statement.Close()
	for _, fileID := range fileIDs {
		if _, err := statement.ExecContext(ctx, deviceID, fileID, syncedAt); err != nil {
			return fmt.Errorf("insert sync ack row: %w", err)
		}
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit sync ack transaction: %w", err)
	}
	return nil
}

func (repository *SyncRepository) ensureFilesExist(ctx context.Context, fileIDs []string) error {
	placeholders := make([]string, len(fileIDs))
	arguments := make([]any, len(fileIDs))
	for index, fileID := range fileIDs {
		placeholders[index] = "?"
		arguments[index] = fileID
	}
	query := fmt.Sprintf("SELECT COUNT(*) FROM files WHERE id IN (%s)", strings.Join(placeholders, ","))
	var count int
	if err := repository.database.QueryRowContext(ctx, query, arguments...).Scan(&count); err != nil {
		return fmt.Errorf("count ack file ids: %w", err)
	}
	if count != len(fileIDs) {
		return ErrUnknownFileID
	}
	return nil
}

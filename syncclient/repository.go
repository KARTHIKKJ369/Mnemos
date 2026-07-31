package syncclient

import (
	"context"
	"database/sql"
	"fmt"
	_ "modernc.org/sqlite"
	"time"
)

type repository struct{ db *sql.DB }

func openRepository(path string) (*repository, error) {
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("open local sync database: %w", err)
	}
	db.SetMaxOpenConns(1)
	_, err = db.Exec(`PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS sync_state (key TEXT PRIMARY KEY, value INTEGER NOT NULL); CREATE TABLE IF NOT EXISTS local_files (file_id TEXT PRIMARY KEY, hash TEXT NOT NULL, filename TEXT NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL, local_path TEXT NOT NULL, downloaded_at INTEGER NOT NULL, last_sync INTEGER NOT NULL, acknowledged INTEGER NOT NULL DEFAULT 0, temporary_path TEXT); CREATE UNIQUE INDEX IF NOT EXISTS idx_local_files_hash ON local_files(hash);`)
	if err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize local sync database: %w", err)
	}
	return &repository{db}, nil
}
func (r *repository) Close() error { return r.db.Close() }
func (r *repository) cursor(ctx context.Context) (*int64, error) {
	var value int64
	err := r.db.QueryRowContext(ctx, "SELECT value FROM sync_state WHERE key='next_since'").Scan(&value)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &value, nil
}
func (r *repository) setCursor(ctx context.Context, value int64) error {
	_, err := r.db.ExecContext(ctx, "INSERT INTO sync_state (key,value) VALUES ('next_since',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", value)
	return err
}
func (r *repository) completed(ctx context.Context, id string) (bool, error) {
	var path string
	err := r.db.QueryRowContext(ctx, "SELECT local_path FROM local_files WHERE file_id=?", id).Scan(&path)
	if err == sql.ErrNoRows {
		return false, nil
	}
	return err == nil, err
}
func (r *repository) save(ctx context.Context, f File, path string, ack bool) error {
	_, err := r.db.ExecContext(ctx, `INSERT INTO local_files(file_id,hash,filename,mime_type,size_bytes,local_path,downloaded_at,last_sync,acknowledged,temporary_path) VALUES(?,?,?,?,?,?,?,?,?,NULL) ON CONFLICT(file_id) DO UPDATE SET hash=excluded.hash,filename=excluded.filename,mime_type=excluded.mime_type,size_bytes=excluded.size_bytes,local_path=excluded.local_path,downloaded_at=excluded.downloaded_at,last_sync=excluded.last_sync,acknowledged=excluded.acknowledged,temporary_path=NULL`, f.ID, f.Hash, f.Filename, f.MIMEType, f.Size, path, time.Now().UnixMilli(), time.Now().UnixMilli(), boolInt(ack))
	return err
}
func (r *repository) markAcknowledged(ctx context.Context, ids []string) error {
	for _, id := range ids {
		if _, err := r.db.ExecContext(ctx, "UPDATE local_files SET acknowledged=1,last_sync=? WHERE file_id=?", time.Now().UnixMilli(), id); err != nil {
			return err
		}
	}
	return nil
}
func boolInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

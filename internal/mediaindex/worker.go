package mediaindex

import (
	"context"
	"database/sql"
	"fmt"
	_ "golang.org/x/image/webp"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"log/slog"
	"photovault/internal/files"
	"photovault/internal/storage"
	"sync"
	"time"
)

type Worker struct {
	repo   *Repository
	files  *files.Repository
	blobs  *storage.BlobStore
	logger *slog.Logger
	cancel context.CancelFunc
	done   chan struct{}
	once   sync.Once
}

func NewWorker(repo *Repository, files *files.Repository, blobs *storage.BlobStore, logger *slog.Logger) *Worker {
	return &Worker{repo: repo, files: files, blobs: blobs, logger: logger, done: make(chan struct{})}
}
func (w *Worker) Start(parent context.Context) {
	ctx, cancel := context.WithCancel(parent)
	w.cancel = cancel
	go w.run(ctx)
}
func (w *Worker) Stop(ctx context.Context) error {
	w.once.Do(func() {
		if w.cancel != nil {
			w.cancel()
		}
	})
	select {
	case <-w.done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
func (w *Worker) run(ctx context.Context) {
	defer close(w.done)
	tick := time.NewTicker(500 * time.Millisecond)
	defer tick.Stop()
	for {
		var id string
		err := w.repo.db.QueryRowContext(ctx, "SELECT file_id FROM media_index_jobs WHERE state='pending' AND next_attempt_at<=? ORDER BY next_attempt_at LIMIT 1", time.Now().UnixMilli()).Scan(&id)
		if err == nil {
			_, _ = w.repo.db.ExecContext(ctx, "UPDATE media_index_jobs SET state='processing',attempts=attempts+1 WHERE file_id=?", id)
			if err = w.index(ctx, id); err != nil {
				_, _ = w.repo.db.ExecContext(ctx, "UPDATE media_index_jobs SET state='pending',next_attempt_at=? WHERE file_id=?", time.Now().Add(time.Second).UnixMilli(), id)
				w.logger.Warn("index media retry", "file_id", id, "error", err)
			} else {
				_, _ = w.repo.db.ExecContext(ctx, "UPDATE media_index_jobs SET state='done' WHERE file_id=?", id)
			}
			continue
		}
		if err != sql.ErrNoRows && ctx.Err() == nil {
			w.logger.Error("claim media index job", "error", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}
func (w *Worker) index(ctx context.Context, id string) error {
	f, found, err := w.files.GetFileByID(ctx, id)
	if err != nil {
		return err
	}
	if !found {
		return nil
	}
	var width, height *int
	if f.MIMEType == "image/jpeg" || f.MIMEType == "image/png" || f.MIMEType == "image/gif" || f.MIMEType == "image/webp" {
		blob, err := w.blobs.Open(f.StoragePath)
		if err != nil {
			return err
		}
		config, _, decodeErr := image.DecodeConfig(blob)
		blob.Close()
		if decodeErr == nil {
			width = &config.Width
			height = &config.Height
		}
	}
	if err := w.repo.Upsert(ctx, f, width, height); err != nil {
		return fmt.Errorf("upsert media index: %w", err)
	}
	return nil
}

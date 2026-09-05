package mediaindex

import (
	"context"
	"database/sql"
	"fmt"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	_ "golang.org/x/image/webp"
	"github.com/rwcarlsen/goexif/exif"

	"photovault/internal/files"
	"photovault/internal/storage"
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

var filenameDateRegexes = []*regexp.Regexp{
	// 20240815_142301 or IMG_20240815_142301 or VID_20240815_142301 or PXL_20240815_142301
	regexp.MustCompile(`(?:^|[^0-9])(20\d\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[_-]([01]\d|2[0-3])([0-5]\d)([0-5]\d)`),
	// 2024-08-15 14.23.01 or 2024-08-15_14-23-01 or 2024-08-15T14:23:01
	regexp.MustCompile(`(?:^|[^0-9])(20\d\d)[-](0[1-9]|1[0-2])[-](0[1-9]|[12]\d|3[01])[T _-]([01]\d|2[0-3])[-.:]([0-5]\d)[-.:]([0-5]\d)`),
	// Just date: 20240815 or 2024-08-15
	regexp.MustCompile(`(?:^|[^0-9])(20\d\d)[-_]?(0[1-9]|1[0-2])[-_]?(0[1-9]|[12]\d|3[01])(?:[^0-9]|$)`),
}

func parseFilenameDate(filename string) *time.Time {
	base := filepath.Base(filename)
	for idx, re := range filenameDateRegexes {
		m := re.FindStringSubmatch(base)
		if len(m) >= 4 {
			year, _ := strconv.Atoi(m[1])
			month, _ := strconv.Atoi(m[2])
			day, _ := strconv.Atoi(m[3])
			hour, min, sec := 12, 0, 0
			if len(m) >= 7 && idx < 2 {
				h, _ := strconv.Atoi(m[4])
				mi, _ := strconv.Atoi(m[5])
				s, _ := strconv.Atoi(m[6])
				hour, min, sec = h, mi, s
			}
			t := time.Date(year, time.Month(month), day, hour, min, sec, 0, time.UTC)
			return &t
		}
	}
	return nil
}

func extractMetadata(blob *os.File, mimeType, filename string) ExtractedMetadata {
	meta := ExtractedMetadata{}

	if strings.HasPrefix(mimeType, "image/") {
		_, _ = blob.Seek(0, io.SeekStart)
		if config, _, err := image.DecodeConfig(blob); err == nil {
			meta.Width = &config.Width
			meta.Height = &config.Height
		}

		_, _ = blob.Seek(0, io.SeekStart)
		if x, err := exif.Decode(blob); err == nil {
			if tm, err := x.DateTime(); err == nil && !tm.IsZero() {
				utc := tm.UTC()
				meta.TakenAt = &utc
			}
			if makeTag, err := x.Get(exif.Make); err == nil {
				if s, err := makeTag.StringVal(); err == nil {
					clean := strings.TrimSpace(strings.Trim(s, "\x00\""))
					if clean != "" {
						meta.CameraMake = &clean
					}
				}
			}
			if modelTag, err := x.Get(exif.Model); err == nil {
				if s, err := modelTag.StringVal(); err == nil {
					clean := strings.TrimSpace(strings.Trim(s, "\x00\""))
					if clean != "" {
						meta.CameraModel = &clean
					}
				}
			}
			if lat, lon, err := x.LatLong(); err == nil {
				meta.GPSLat = &lat
				meta.GPSLon = &lon
			}
		}
	}

	if meta.TakenAt == nil {
		meta.TakenAt = parseFilenameDate(filename)
	}

	return meta
}

func (w *Worker) index(ctx context.Context, id string) error {
	f, found, err := w.files.GetFileByID(ctx, id)
	if err != nil {
		return err
	}
	if !found {
		return nil
	}

	meta := ExtractedMetadata{}
	blob, err := w.blobs.Open(f.StoragePath)
	if err == nil {
		meta = extractMetadata(blob, f.MIMEType, f.OriginalFilename)
		_ = blob.Close()
	} else {
		meta.TakenAt = parseFilenameDate(f.OriginalFilename)
	}

	if err := w.repo.Upsert(ctx, f, meta); err != nil {
		return fmt.Errorf("upsert media index: %w", err)
	}
	return nil
}


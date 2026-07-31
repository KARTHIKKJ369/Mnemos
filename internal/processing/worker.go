package processing

import (
	"context"
	"errors"
	"fmt"
	"image"
	_ "image/gif"
	"image/jpeg"
	_ "image/png"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"time"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"photovault/internal/files"
	"photovault/internal/storage"
)

var ErrSkipped = errors.New("media processing skipped")

type Result struct{ ThumbnailPath, PreviewPath string }
type Processor interface {
	Process(context.Context, files.File) (Result, error)
}

// Worker owns one cancellable background loop and persists every job state transition.
type Worker struct {
	repository *Repository
	processor  Processor
	logger     *slog.Logger
	cancel     context.CancelFunc
	done       chan struct{}
	once       sync.Once
}

func NewWorker(repository *Repository, processor Processor, logger *slog.Logger) *Worker {
	return &Worker{repository: repository, processor: processor, logger: logger, done: make(chan struct{})}
}

func (w *Worker) Start(parent context.Context) error {
	if err := w.repository.Recover(parent); err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(parent)
	w.cancel = cancel
	go w.run(ctx)
	return nil
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
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		file, found, err := w.repository.Claim(ctx, time.Now())
		if err != nil && !errors.Is(err, context.Canceled) {
			w.logger.Error("claim media job", "error", err)
		}
		if found {
			result, processErr := w.processor.Process(ctx, file)
			if processErr == nil || errors.Is(processErr, ErrSkipped) {
				if err := w.repository.Complete(ctx, file.ID, result.ThumbnailPath, result.PreviewPath); err != nil {
					w.logger.Error("complete media job", "file_id", file.ID, "error", err)
				} else {
					w.logger.Info("media job completed", "file_id", file.ID, "thumbnail", result.ThumbnailPath != "", "preview", result.PreviewPath != "", "skipped", errors.Is(processErr, ErrSkipped))
				}
			} else if ctx.Err() == nil {
				if err := w.repository.Retry(ctx, file.ID, time.Second, processErr); err != nil {
					w.logger.Error("retry media job", "file_id", file.ID, "error", err)
				} else {
					w.logger.Warn("media job failed; retrying", "file_id", file.ID, "error", processErr)
				}
			}
			continue
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

// MediaProcessor creates derived files without reading originals into memory.
type MediaProcessor struct {
	blobs                      *storage.BlobStore
	root, thumbnails, previews string
}

func NewMediaProcessor(blobs *storage.BlobStore, layout storage.Layout) *MediaProcessor {
	return &MediaProcessor{blobs: blobs, root: layout.Root, thumbnails: layout.Thumbnails, previews: layout.Previews}
}

func (p *MediaProcessor) Process(ctx context.Context, file files.File) (Result, error) {
	switch file.MIMEType {
	case "image/jpeg", "image/png", "image/webp", "image/gif":
		path, err := p.thumbnail(ctx, file)
		if err != nil {
			return Result{}, err
		}
		return Result{ThumbnailPath: path}, nil
	case "video/mp4", "video/quicktime", "video/x-matroska", "video/x-msvideo":
		return p.video(ctx, file)
	default:
		return Result{}, ErrSkipped
	}
}

func (p *MediaProcessor) thumbnail(ctx context.Context, file files.File) (string, error) {
	original, err := p.blobs.Open(file.StoragePath)
	if err != nil {
		return "", err
	}
	defer original.Close()
	source, _, err := image.Decode(original)
	if err != nil {
		return "", fmt.Errorf("decode image: %w", err)
	}
	bounds := source.Bounds()
	width, height := bounds.Dx(), bounds.Dy()
	if width <= 0 || height <= 0 {
		return "", fmt.Errorf("invalid image dimensions")
	}
	if width > 256 || height > 256 {
		if width >= height {
			height = height * 256 / width
			width = 256
		} else {
			width = width * 256 / height
			height = 256
		}
	}
	destination := image.NewRGBA(image.Rect(0, 0, width, height))
	draw.CatmullRom.Scale(destination, destination.Bounds(), source, bounds, draw.Over, nil)
	path := filepath.Join(p.thumbnails, file.Hash+".jpg")
	if err := writeJPEG(path, destination); err != nil {
		return "", err
	}
	return filepath.ToSlash(filepath.Join("thumbnails", file.Hash+".jpg")), nil
}

func writeJPEG(path string, image image.Image) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return fmt.Errorf("create thumbnail directory: %w", err)
	}
	temporary, err := os.CreateTemp(filepath.Dir(path), ".thumbnail-*")
	if err != nil {
		return fmt.Errorf("create thumbnail: %w", err)
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := jpeg.Encode(temporary, image, &jpeg.Options{Quality: 85}); err != nil {
		temporary.Close()
		return fmt.Errorf("encode thumbnail: %w", err)
	}
	if err := temporary.Close(); err != nil {
		return fmt.Errorf("close thumbnail: %w", err)
	}
	if err := os.Rename(temporaryPath, path); err != nil {
		return fmt.Errorf("finalize thumbnail: %w", err)
	}
	return nil
}

func (p *MediaProcessor) video(ctx context.Context, file files.File) (Result, error) {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		return Result{}, ErrSkipped
	}
	originalPath := filepath.Join(p.root, filepath.FromSlash(file.StoragePath))
	thumbnail := filepath.Join(p.thumbnails, file.Hash+".jpg")
	preview := filepath.Join(p.previews, file.Hash+".mp4")
	if err := os.MkdirAll(filepath.Dir(thumbnail), 0o750); err != nil {
		return Result{}, err
	}
	if err := os.MkdirAll(filepath.Dir(preview), 0o750); err != nil {
		return Result{}, err
	}
	if output, err := exec.CommandContext(ctx, "ffmpeg", "-y", "-ss", "1", "-i", originalPath, "-frames:v", "1", "-q:v", "2", thumbnail).CombinedOutput(); err != nil {
		return Result{}, fmt.Errorf("generate video thumbnail: %w: %s", err, output)
	}
	if output, err := exec.CommandContext(ctx, "ffmpeg", "-y", "-i", originalPath, "-vf", "scale=-2:480", "-c:v", "libx264", "-c:a", "aac", "-movflags", "+faststart", preview).CombinedOutput(); err != nil {
		return Result{}, fmt.Errorf("generate video preview: %w: %s", err, output)
	}
	return Result{ThumbnailPath: filepath.ToSlash(filepath.Join("thumbnails", file.Hash+".jpg")), PreviewPath: filepath.ToSlash(filepath.Join("previews", file.Hash+".mp4"))}, nil
}

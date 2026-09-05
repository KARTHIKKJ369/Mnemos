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
	"runtime"
	"sync"
	"time"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"photovault/internal/files"
	"photovault/internal/storage"
)

// workerConcurrency controls how many media jobs process simultaneously.
// Hardware encode/decode engines (VideoToolbox, CoreImage) are not CPU-bound,
// so running multiple jobs concurrently is safe on Apple Silicon.
const workerConcurrency = 3

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
	sem := make(chan struct{}, workerConcurrency)
	var wg sync.WaitGroup
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		file, found, err := w.repository.Claim(ctx, time.Now())
		if err != nil && !errors.Is(err, context.Canceled) {
			w.logger.Error("claim media job", "error", err)
		}
		if found {
			wg.Add(1)
			select {
			case sem <- struct{}{}:
			case <-ctx.Done():
				wg.Done()
				wg.Wait()
				return
			}
			go func(f files.File) {
				defer wg.Done()
				defer func() { <-sem }()
				result, processErr := w.processor.Process(ctx, f)
				if processErr == nil || errors.Is(processErr, ErrSkipped) {
					if err := w.repository.Complete(ctx, f.ID, result.ThumbnailPath, result.PreviewPath); err != nil {
						w.logger.Error("complete media job", "file_id", f.ID, "error", err)
					} else {
						w.logger.Info("media job completed", "file_id", f.ID, "thumbnail", result.ThumbnailPath != "", "preview", result.PreviewPath != "", "skipped", errors.Is(processErr, ErrSkipped))
					}
				} else if ctx.Err() == nil {
					if err := w.repository.Retry(ctx, f.ID, time.Second, processErr); err != nil {
						w.logger.Error("retry media job", "file_id", f.ID, "error", err)
					} else {
						w.logger.Warn("media job failed; retrying", "file_id", f.ID, "error", processErr)
					}
				}
			}(file)
			continue
		}
		select {
		case <-ctx.Done():
			wg.Wait()
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
	path := filepath.Join(p.thumbnails, file.Hash+".jpg")
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return "", fmt.Errorf("create thumbnail directory: %w", err)
	}
	originalPath := filepath.Join(p.root, filepath.FromSlash(file.StoragePath))

	// Prefer macOS sips for hardware-accelerated thumbnailing (CoreImage/Metal).
	// Handles HEIC, RAW, WebP, JPEG, PNG natively with near-zero CPU.
	if runtime.GOOS == "darwin" {
		if sipsPath, err := exec.LookPath("sips"); err == nil {
			if err := p.thumbnailSips(ctx, sipsPath, originalPath, path); err == nil {
				return filepath.ToSlash(filepath.Join("thumbnails", file.Hash+".jpg")), nil
			}
			// Fall through to Go-based processing on sips failure
		}
	}

	// Software fallback: pure Go image decoding and Catmull-Rom scaling.
	return p.thumbnailGo(file, path)
}

// thumbnailSips uses macOS sips (backed by CoreImage) for hardware-accelerated image scaling.
func (p *MediaProcessor) thumbnailSips(ctx context.Context, sipsPath, originalPath, outputPath string) error {
	// sips --resampleHeightWidthMax scales the longest edge while preserving aspect ratio.
	// Output is written as JPEG with quality 85 (good balance of size vs fidelity).
	tmpPath := outputPath + ".tmp.jpg"
	output, err := exec.CommandContext(ctx, sipsPath,
		"--resampleHeightWidthMax", "720",
		"--setProperty", "format", "jpeg",
		"--setProperty", "formatOptions", "85",
		originalPath,
		"--out", tmpPath,
	).CombinedOutput()
	if err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("sips thumbnail: %w: %s", err, output)
	}
	if err := os.Rename(tmpPath, outputPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("finalize sips thumbnail: %w", err)
	}
	return nil
}

// thumbnailGo is the software fallback using pure Go image decoding and Catmull-Rom scaling.
func (p *MediaProcessor) thumbnailGo(file files.File, path string) (string, error) {
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
	const maxDim = 720
	if width > maxDim || height > maxDim {
		if width >= height {
			height = height * maxDim / width
			width = maxDim
		} else {
			width = width * maxDim / height
			height = maxDim
		}
	}
	destination := image.NewRGBA(image.Rect(0, 0, width, height))
	draw.CatmullRom.Scale(destination, destination.Bounds(), source, bounds, draw.Over, nil)
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
	if err := jpeg.Encode(temporary, image, &jpeg.Options{Quality: 88}); err != nil {
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

	// Video thumbnail: seek to 1s and extract a single frame.
	// On macOS, use VideoToolbox for hardware-accelerated decoding.
	thumbArgs := []string{"-y"}
	if runtime.GOOS == "darwin" {
		thumbArgs = append(thumbArgs, "-hwaccel", "videotoolbox")
	}
	thumbArgs = append(thumbArgs, "-ss", "1", "-i", originalPath,
		"-vf", "scale='min(720,iw)':-2",
		"-frames:v", "1", "-q:v", "2", thumbnail)
	if output, err := exec.CommandContext(ctx, "ffmpeg", thumbArgs...).CombinedOutput(); err != nil {
		return Result{}, fmt.Errorf("generate video thumbnail: %w: %s", err, output)
	}

	// Video preview: try hardware-accelerated h264_videotoolbox on macOS,
	// fall back to software libx264 if VideoToolbox is unavailable (Docker/Linux).
	if err := p.videoPreviewHardware(ctx, originalPath, preview); err != nil {
		if err := p.videoPreviewSoftware(ctx, originalPath, preview); err != nil {
			return Result{}, err
		}
	}

	return Result{
		ThumbnailPath: filepath.ToSlash(filepath.Join("thumbnails", file.Hash+".jpg")),
		PreviewPath:   filepath.ToSlash(filepath.Join("previews", file.Hash+".mp4")),
	}, nil
}

// videoPreviewHardware generates a video preview using Apple's VideoToolbox H.264 hardware encoder.
// ~10× faster than software libx264, near-zero CPU utilization on Apple Silicon.
func (p *MediaProcessor) videoPreviewHardware(ctx context.Context, input, output string) error {
	if runtime.GOOS != "darwin" {
		return fmt.Errorf("videotoolbox not available on %s", runtime.GOOS)
	}
	combined, err := exec.CommandContext(ctx, "ffmpeg", "-y",
		"-hwaccel", "videotoolbox",
		"-i", input,
		"-vf", "scale='min(1080,iw)':-2",
		"-c:v", "h264_videotoolbox",
		"-q:v", "65",
		"-profile:v", "high",
		"-level", "4.1",
		"-c:a", "aac", "-b:a", "128k",
		"-movflags", "+faststart",
		output,
	).CombinedOutput()
	if err != nil {
		os.Remove(output)
		return fmt.Errorf("videotoolbox preview: %w: %s", err, combined)
	}
	return nil
}

// videoPreviewSoftware generates a video preview using software libx264 encoding.
// Used as fallback when VideoToolbox is unavailable (Linux, Docker, CI).
func (p *MediaProcessor) videoPreviewSoftware(ctx context.Context, input, output string) error {
	combined, err := exec.CommandContext(ctx, "ffmpeg", "-y",
		"-i", input,
		"-vf", "scale='min(1080,iw)':-2",
		"-c:v", "libx264",
		"-preset", "veryfast",
		"-crf", "20",
		"-c:a", "aac", "-b:a", "128k",
		"-movflags", "+faststart",
		output,
	).CombinedOutput()
	if err != nil {
		os.Remove(output)
		return fmt.Errorf("software preview: %w: %s", err, combined)
	}
	return nil
}

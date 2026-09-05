package scanning

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/storage"
)

// ScanResult contains metrics about a completed folder scan.
type ScanResult struct {
	Scanned        int `json:"scanned"`
	Imported       int `json:"imported"`
	AlreadyIndexed int `json:"already_indexed"`
	Errors         int `json:"errors"`
}

// JobEnqueuer enqueues background processing for newly indexed media files.
type JobEnqueuer interface {
	Enqueue(context.Context, string) error
}

var mediaExtensions = map[string]bool{
	".jpg":  true,
	".jpeg": true,
	".png":  true,
	".gif":  true,
	".webp": true,
	".heic": true,
	".heif": true,
	".tiff": true,
	".tif":  true,
	".bmp":  true,
	".mp4":  true,
	".mov":  true,
	".m4v":  true,
	".avi":  true,
	".mkv":  true,
	".webm": true,
}

// extensionMIMEOverrides provides reliable MIME types for formats that
// http.DetectContentType cannot reliably distinguish from binary data.
var extensionMIMEOverrides = map[string]string{
	".mp4":  "video/mp4",
	".m4v":  "video/mp4",
	".mov":  "video/quicktime",
	".avi":  "video/x-msvideo",
	".mkv":  "video/x-matroska",
	".webm": "video/webm",
	".heic": "image/heic",
	".heif": "image/heif",
	".tiff": "image/tiff",
	".tif":  "image/tiff",
	".bmp":  "image/bmp",
}

// mimeForFile returns an accurate MIME type for a media file.
// It first checks extension overrides, then falls back to content sniffing.
func mimeForFile(ext string, sniffed string) string {
	if m, ok := extensionMIMEOverrides[strings.ToLower(ext)]; ok {
		return m
	}
	return sniffed
}

// FolderScanner walks a directory tree and ingests unindexed photos and videos.
type FolderScanner struct {
	blobStore   *storage.BlobStore
	fileStore   *files.Repository
	logger      *slog.Logger
	jobs        []JobEnqueuer
	storageRoot string
}

// NewFolderScanner creates a new scanner instance.
func NewFolderScanner(blobStore *storage.BlobStore, fileStore *files.Repository, storageRoot string, logger *slog.Logger, jobs ...JobEnqueuer) *FolderScanner {
	return &FolderScanner{
		blobStore:   blobStore,
		fileStore:   fileStore,
		logger:      logger,
		jobs:        jobs,
		storageRoot: storageRoot,
	}
}

// Scan walks targetPath (or storageRoot if targetPath is empty) and ingests media files.
func (scanner *FolderScanner) Scan(ctx context.Context, targetPath string, adminDevice devices.Device) (ScanResult, error) {
	if strings.TrimSpace(targetPath) == "" {
		targetPath = scanner.storageRoot
	}
	cleanTarget, err := filepath.Abs(targetPath)
	if err != nil {
		return ScanResult{}, fmt.Errorf("resolve target path: %w", err)
	}

	info, err := os.Stat(cleanTarget)
	if err != nil {
		return ScanResult{}, fmt.Errorf("target path not accessible: %w", err)
	}
	if !info.IsDir() {
		return ScanResult{}, fmt.Errorf("target path is not a directory: %s", cleanTarget)
	}

	result := ScanResult{}
	scanner.logger.Info("starting folder scan", "path", cleanTarget, "admin_device", adminDevice.Name)

	walkErr := filepath.WalkDir(cleanTarget, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			result.Errors++
			scanner.logger.Warn("cannot access path in scan", "path", path, "err", err)
			return nil
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		if d.IsDir() {
			name := d.Name()
			if strings.HasPrefix(name, ".") || isIgnoredDirName(name) {
				return filepath.SkipDir
			}
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if !mediaExtensions[ext] {
			return nil
		}

		result.Scanned++
		importErr := scanner.importFile(ctx, path, adminDevice)
		if importErr != nil {
			if errors.Is(importErr, errAlreadyIndexed) {
				result.AlreadyIndexed++
				return nil
			}
			result.Errors++
			scanner.logger.Error("failed to import scanned file", "path", path, "err", importErr)
			return nil
		}

		result.Imported++
		return nil
	})

	scanner.logger.Info("completed folder scan",
		"scanned", result.Scanned,
		"imported", result.Imported,
		"already_indexed", result.AlreadyIndexed,
		"errors", result.Errors,
	)

	return result, walkErr
}

var errAlreadyIndexed = errors.New("file already indexed")

func (scanner *FolderScanner) importFile(ctx context.Context, path string, adminDevice devices.Device) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("open file: %w", err)
	}
	defer file.Close()

	stat, err := file.Stat()
	if err != nil {
		return fmt.Errorf("stat file: %w", err)
	}
	if stat.Size() == 0 {
		return nil
	}

	// Calculate SHA-256 and sniff content type
	hasher := sha256.New()
	sniffBuf := make([]byte, 512)
	n, readErr := file.Read(sniffBuf)
	if readErr != nil && readErr != io.EOF {
		return fmt.Errorf("read prefix: %w", readErr)
	}
	sniffed := http.DetectContentType(sniffBuf[:n])
	mimeType := mimeForFile(filepath.Ext(path), sniffed)
	hasher.Write(sniffBuf[:n])

	if _, err := io.Copy(hasher, file); err != nil {
		return fmt.Errorf("hash content: %w", err)
	}

	hash := hex.EncodeToString(hasher.Sum(nil))

	// Check if already in database
	_, found, err := scanner.fileStore.FindExistence(ctx, hash)
	if err == nil && found {
		return errAlreadyIndexed
	}

	// Prepare blob storage
	var storagePath string
	cleanStorageRoot, _ := filepath.Abs(scanner.storageRoot)
	cleanPath, _ := filepath.Abs(path)

	// If file is already inside the storage folder's blobs directory, reuse relative path
	if strings.HasPrefix(cleanPath, filepath.Join(cleanStorageRoot, "blobs")) {
		rel, relErr := filepath.Rel(cleanStorageRoot, cleanPath)
		if relErr == nil {
			storagePath = filepath.ToSlash(rel)
		}
	}

	uploadedAt := stat.ModTime()
	if uploadedAt.IsZero() || uploadedAt.After(time.Now()) {
		uploadedAt = time.Now()
	}

	if storagePath == "" {
		// Need to finalize into blobStore
		tempFile, tempErr := os.CreateTemp("", "scan-import-*")
		if tempErr != nil {
			return fmt.Errorf("create temp file: %w", tempErr)
		}
		tempPath := tempFile.Name()
		defer os.Remove(tempPath)

		if _, seekErr := file.Seek(0, io.SeekStart); seekErr != nil {
			tempFile.Close()
			return fmt.Errorf("rewind source file: %w", seekErr)
		}

		if _, copyErr := io.Copy(tempFile, file); copyErr != nil {
			tempFile.Close()
			return fmt.Errorf("copy to temp: %w", copyErr)
		}
		tempFile.Close()

		var finalizeErr error
		err = scanner.blobStore.WithHashLock(hash, func() error {
			relPath, _, err := scanner.blobStore.Finalize(tempPath, hash, mimeType, adminDevice, uploadedAt)
			if err != nil {
				finalizeErr = err
				return err
			}
			storagePath = relPath
			return nil
		})
		if err != nil {
			return fmt.Errorf("finalize blob: %w", finalizeErr)
		}
	}

	// Insert into database
	input := files.CreateInput{
		Hash:               hash,
		OriginalFilename:   filepath.Base(path),
		MIMEType:           mimeType,
		SizeBytes:          stat.Size(),
		UploadedByDeviceID: adminDevice.ID,
		StoragePath:        storagePath,
		UploadedAt:         uploadedAt,
	}

	createdFile, isNew, createErr := scanner.fileStore.CreateOrGet(ctx, input)
	if createErr != nil {
		return fmt.Errorf("create file record: %w", createErr)
	}

	if !isNew {
		return errAlreadyIndexed
	}

	// Enqueue background processing jobs (thumbnails, preview, media index)
	for _, job := range scanner.jobs {
		if err := job.Enqueue(ctx, createdFile.ID); err != nil {
			scanner.logger.Warn("enqueue job failed for scanned file", "file_id", createdFile.ID, "err", err)
		}
	}

	return nil
}

func isIgnoredDirName(name string) bool {
	switch strings.ToLower(name) {
	case "derived", "thumbnails", "previews", "node_modules", ".locked", ".git", ".upload":
		return true
	default:
		return false
	}
}

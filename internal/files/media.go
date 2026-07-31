package files

import (
	"context"
	"errors"
	"fmt"
	"os"
)

// ErrFileNotFound indicates that no metadata exists for the requested file ID.
var ErrFileNotFound = errors.New("file not found")
var ErrDerivativeNotFound = errors.New("derived media not found")

// FileGetter retrieves metadata for a file ID.
type FileGetter interface {
	GetFileByID(ctx context.Context, id string) (File, bool, error)
}

// BlobOpener safely opens a stored blob using its persisted relative path.
type BlobOpener interface {
	Open(relativePath string) (*os.File, error)
}

// MediaService coordinates media metadata retrieval and blob access.
type MediaService struct {
	repository FileGetter
	blobs      BlobOpener
}

// NewMediaService constructs a media retrieval service.
func NewMediaService(repository FileGetter, blobs BlobOpener) *MediaService {
	return &MediaService{repository: repository, blobs: blobs}
}

// OpenOriginal retrieves metadata and opens the original blob for streaming. The caller closes it.
func (service *MediaService) OpenOriginal(ctx context.Context, id string) (File, *os.File, error) {
	file, found, err := service.repository.GetFileByID(ctx, id)
	if err != nil {
		return File{}, nil, fmt.Errorf("get file metadata: %w", err)
	}
	if !found {
		return File{}, nil, ErrFileNotFound
	}
	blob, err := service.blobs.Open(file.StoragePath)
	if err != nil {
		return File{}, nil, fmt.Errorf("open original blob: %w", err)
	}
	return file, blob, nil
}

// OpenThumbnail opens a generated thumbnail when processing has completed it.
func (service *MediaService) OpenThumbnail(ctx context.Context, id string) (File, *os.File, error) {
	return service.openDerived(ctx, id, true)
}

// OpenPreview opens a generated preview when processing has completed it.
func (service *MediaService) OpenPreview(ctx context.Context, id string) (File, *os.File, error) {
	return service.openDerived(ctx, id, false)
}

func (service *MediaService) openDerived(ctx context.Context, id string, thumbnail bool) (File, *os.File, error) {
	file, found, err := service.repository.GetFileByID(ctx, id)
	if err != nil {
		return File{}, nil, fmt.Errorf("get file metadata: %w", err)
	}
	if !found {
		return File{}, nil, ErrFileNotFound
	}
	path := file.PreviewPath
	if thumbnail {
		path = file.ThumbnailPath
	}
	if path == "" {
		return File{}, nil, ErrDerivativeNotFound
	}
	blob, err := service.blobs.Open(path)
	if err != nil {
		return File{}, nil, fmt.Errorf("open derived blob: %w", err)
	}
	return file, blob, nil
}

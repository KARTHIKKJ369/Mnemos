package synchronization

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
)

// Repository provides sync persistence operations.
type Repository interface {
	Diff(ctx context.Context, deviceID string, since *int64, limit int) ([]File, error)
	Ack(ctx context.Context, deviceID string, fileIDs []string, syncedAt int64) error
}

// SyncService validates and coordinates sync requests.
type SyncService struct {
	repository   Repository
	defaultLimit int
	maximumLimit int
	ackMaxBatch  int
	now          func() time.Time
}

// NewSyncService constructs a sync service with configured pagination and ack limits.
func NewSyncService(repository Repository, defaultLimit, maximumLimit, ackMaxBatch int) (*SyncService, error) {
	if defaultLimit <= 0 {
		return nil, fmt.Errorf("default sync limit must be positive")
	}
	if maximumLimit <= 0 || maximumLimit > 1000 {
		return nil, fmt.Errorf("maximum sync limit must be between 1 and 1000")
	}
	if defaultLimit > maximumLimit {
		return nil, fmt.Errorf("default sync limit exceeds maximum sync limit")
	}
	if ackMaxBatch <= 0 {
		return nil, fmt.Errorf("sync ack batch size must be positive")
	}
	return &SyncService{
		repository:   repository,
		defaultLimit: defaultLimit,
		maximumLimit: maximumLimit,
		ackMaxBatch:  ackMaxBatch,
		now:          time.Now,
	}, nil
}

// Diff returns a validated page of unsynchronized files for one authenticated device.
func (service *SyncService) Diff(ctx context.Context, deviceID string, since *int64, requestedLimit *int) (Diff, error) {
	if since != nil && *since < 0 {
		return Diff{}, ErrInvalidSince
	}
	limit := service.defaultLimit
	if requestedLimit != nil {
		limit = *requestedLimit
	}
	if limit <= 0 || limit > service.maximumLimit {
		return Diff{}, fmt.Errorf("%w: limit must be between 1 and %d", ErrInvalidLimit, service.maximumLimit)
	}
	files, err := service.repository.Diff(ctx, deviceID, since, limit)
	if err != nil {
		return Diff{}, fmt.Errorf("query sync diff: %w", err)
	}
	result := Diff{Files: files}
	if len(files) > 0 {
		nextSince := files[len(files)-1].UploadedAt
		result.NextSince = &nextSince
	}
	return result, nil
}

// Ack records that an authenticated device has synchronized the supplied file IDs.
func (service *SyncService) Ack(ctx context.Context, deviceID string, fileIDs []string) (int, error) {
	if len(fileIDs) == 0 {
		return 0, ErrEmptyBatch
	}
	if len(fileIDs) > service.ackMaxBatch {
		return 0, ErrBatchTooLarge
	}
	seen := make(map[string]struct{}, len(fileIDs))
	for _, fileID := range fileIDs {
		if _, err := uuid.Parse(fileID); err != nil {
			return 0, ErrInvalidFileID
		}
		if _, ok := seen[fileID]; ok {
			return 0, ErrDuplicateFileID
		}
		seen[fileID] = struct{}{}
	}
	if err := service.repository.Ack(ctx, deviceID, fileIDs, service.now().UnixMilli()); err != nil {
		return 0, fmt.Errorf("ack sync state: %w", err)
	}
	return len(fileIDs), nil
}

package synchronization

import (
	"context"
	"errors"
	"testing"
)

type stubRepository struct {
	diffFiles []File
	diffErr   error
	ackErr    error
	ackCalls  int
}

func (repository *stubRepository) Diff(context.Context, string, *int64, int) ([]File, error) {
	return repository.diffFiles, repository.diffErr
}

func (repository *stubRepository) Ack(context.Context, string, []string, int64) error {
	repository.ackCalls++
	return repository.ackErr
}

func TestSyncServiceDiffAppliesDefaultAndMaximumLimits(t *testing.T) {
	t.Parallel()

	service, err := NewSyncService(&stubRepository{}, 100, 500, 250)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}
	if _, err := service.Diff(context.Background(), "device-a", nil, nil); err != nil {
		t.Fatalf("default diff: %v", err)
	}
	limit := 250
	if _, err := service.Diff(context.Background(), "device-a", nil, &limit); err != nil {
		t.Fatalf("requested diff: %v", err)
	}
	invalid := 501
	if _, err := service.Diff(context.Background(), "device-a", nil, &invalid); !errors.Is(err, ErrInvalidLimit) {
		t.Fatalf("error = %v, want ErrInvalidLimit", err)
	}
	negativeSince := int64(-1)
	if _, err := service.Diff(context.Background(), "device-a", &negativeSince, nil); !errors.Is(err, ErrInvalidSince) {
		t.Fatalf("error = %v, want ErrInvalidSince", err)
	}
}

func TestSyncServiceDiffReturnsNextSince(t *testing.T) {
	t.Parallel()

	repository := &stubRepository{diffFiles: []File{{FileID: "file-a", UploadedAt: 100}, {FileID: "file-b", UploadedAt: 200}}}
	service, err := NewSyncService(repository, 100, 500, 250)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}
	result, err := service.Diff(context.Background(), "device-a", nil, nil)
	if err != nil {
		t.Fatalf("diff: %v", err)
	}
	if result.NextSince == nil || *result.NextSince != 200 {
		t.Fatalf("next_since = %v, want 200", result.NextSince)
	}
}

func TestSyncServiceAckValidatesBatch(t *testing.T) {
	t.Parallel()

	repository := &stubRepository{}
	service, err := NewSyncService(repository, 100, 500, 2)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}
	if _, err := service.Ack(context.Background(), "device-a", nil); !errors.Is(err, ErrEmptyBatch) {
		t.Fatalf("empty batch error = %v, want ErrEmptyBatch", err)
	}
	if _, err := service.Ack(context.Background(), "device-a", []string{
		"00000000-0000-0000-0000-000000000001",
		"00000000-0000-0000-0000-000000000001",
	}); !errors.Is(err, ErrDuplicateFileID) {
		t.Fatalf("duplicate error = %v, want ErrDuplicateFileID", err)
	}
	if _, err := service.Ack(context.Background(), "device-a", []string{"not-a-uuid"}); !errors.Is(err, ErrInvalidFileID) {
		t.Fatalf("invalid uuid error = %v, want ErrInvalidFileID", err)
	}
	if _, err := service.Ack(context.Background(), "device-a", []string{
		"00000000-0000-0000-0000-000000000001",
		"00000000-0000-0000-0000-000000000002",
		"00000000-0000-0000-0000-000000000003",
	}); !errors.Is(err, ErrBatchTooLarge) {
		t.Fatalf("batch size error = %v, want ErrBatchTooLarge", err)
	}
}

func TestSyncServiceAckReturnsAcknowledgedCount(t *testing.T) {
	t.Parallel()

	repository := &stubRepository{}
	service, err := NewSyncService(repository, 100, 500, 250)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}
	acknowledged, err := service.Ack(context.Background(), "device-a", []string{
		"00000000-0000-0000-0000-000000000001",
		"00000000-0000-0000-0000-000000000002",
	})
	if err != nil {
		t.Fatalf("ack: %v", err)
	}
	if acknowledged != 2 || repository.ackCalls != 1 {
		t.Fatalf("acknowledged = %d, ack calls = %d, want 2 and 1", acknowledged, repository.ackCalls)
	}
}

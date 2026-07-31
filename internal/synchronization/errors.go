package synchronization

import "errors"

var (
	// ErrEmptyBatch indicates sync ack was called with no file IDs.
	ErrEmptyBatch = errors.New("empty file_ids batch")
	// ErrDuplicateFileID indicates sync ack contained duplicate file IDs.
	ErrDuplicateFileID = errors.New("duplicate file_id")
	// ErrInvalidFileID indicates a file ID is not a valid UUID.
	ErrInvalidFileID = errors.New("invalid file_id")
	// ErrUnknownFileID indicates a file ID does not exist.
	ErrUnknownFileID = errors.New("unknown file_id")
	// ErrBatchTooLarge indicates sync ack exceeded the configured batch size.
	ErrBatchTooLarge = errors.New("batch exceeds maximum size")
	// ErrInvalidSince indicates a negative sync-diff cursor.
	ErrInvalidSince = errors.New("since must not be negative")
	// ErrInvalidLimit indicates sync-diff limit is outside the allowed range.
	ErrInvalidLimit = errors.New("invalid limit")
)

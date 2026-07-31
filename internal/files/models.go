// Package files manages PhotoVault media metadata and sync state.
package files

import "time"

// File contains stored media metadata needed by upload responses and later API operations.
type File struct {
	ID        string
	Hash      string
	SizeBytes int64
	MIMEType  string
	Status    string
}

// Existence contains only file metadata permitted by the hash-existence API.
type Existence struct {
	FileID    string
	SizeBytes int64
}

// CreateInput is the metadata recorded for a newly uploaded blob.
type CreateInput struct {
	Hash               string
	OriginalFilename   string
	MIMEType           string
	SizeBytes          int64
	UploadedByDeviceID string
	StoragePath        string
	UploadedAt         time.Time
}

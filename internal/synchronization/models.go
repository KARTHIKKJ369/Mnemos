// Package synchronization implements metadata synchronization between PhotoVault devices.
package synchronization

// File is the metadata returned to a device for one unsynchronized file.
type File struct {
	FileID             string
	Hash               string
	Filename           string
	MIMEType           string
	SizeBytes          int64
	ThumbnailAvailable bool
	PreviewAvailable   bool
	UploadedAt         int64
}

// Diff is the complete result of one sync-diff request.
type Diff struct {
	Files     []File
	NextSince *int64
}

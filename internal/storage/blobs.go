package storage

import (
	"fmt"
	"mime"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unicode"

	"photovault/internal/devices"
)

// BlobStore finalizes content-addressed files in the PhotoVault storage layout.
type BlobStore struct {
	layout Layout
	mu     sync.Mutex
	locks  map[string]*hashLock
}

type hashLock struct {
	mu    sync.Mutex
	users int
}

// NewBlobStore constructs a blob store using a prepared storage layout.
func NewBlobStore(layout Layout) *BlobStore {
	return &BlobStore{layout: layout, locks: make(map[string]*hashLock)}
}

// WithHashLock serializes finalization and metadata creation for one content hash.
func (store *BlobStore) WithHashLock(hash string, operation func() error) error {
	store.mu.Lock()
	lock, ok := store.locks[hash]
	if !ok {
		lock = &hashLock{}
		store.locks[hash] = lock
	}
	lock.users++
	store.mu.Unlock()
	lock.mu.Lock()
	defer func() {
		lock.mu.Unlock()
		store.mu.Lock()
		lock.users--
		if lock.users == 0 {
			delete(store.locks, hash)
		}
		store.mu.Unlock()
	}()
	return operation()
}

// Finalize atomically moves a completed temporary file into its content-addressed destination.
func (store *BlobStore) Finalize(temporaryPath, hash, mimeType string, device devices.Device, uploadedAt time.Time) (string, bool, error) {
	extension := extensionForMIMEType(mimeType)
	relativePath := filepath.Join("blobs", "by-device", deviceSlug(device), uploadedAt.Format("2006"), uploadedAt.Format("01"), hash+extension)
	destinationPath := filepath.Join(store.layout.Root, relativePath)
	if _, err := os.Stat(destinationPath); err == nil {
		return filepath.ToSlash(relativePath), true, nil
	} else if !os.IsNotExist(err) {
		return "", false, fmt.Errorf("inspect blob destination: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(destinationPath), 0o750); err != nil {
		return "", false, fmt.Errorf("create blob directory: %w", err)
	}
	if err := os.Rename(temporaryPath, destinationPath); err != nil {
		return "", false, fmt.Errorf("finalize blob: %w", err)
	}
	return filepath.ToSlash(relativePath), false, nil
}

// Remove deletes a finalized blob after its associated metadata transaction fails.
func (store *BlobStore) Remove(relativePath string) error {
	if err := os.Remove(filepath.Join(store.layout.Root, filepath.FromSlash(relativePath))); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove blob: %w", err)
	}
	return nil
}

func extensionForMIMEType(mimeType string) string {
	extensions, err := mime.ExtensionsByType(mimeType)
	if err == nil && len(extensions) > 0 {
		return extensions[0]
	}
	return ".bin"
}

func deviceSlug(device devices.Device) string {
	var builder strings.Builder
	lastWasDash := false
	for _, character := range strings.ToLower(device.Name) {
		if unicode.IsLetter(character) || unicode.IsDigit(character) {
			builder.WriteRune(character)
			lastWasDash = false
			continue
		}
		if builder.Len() > 0 && !lastWasDash {
			builder.WriteByte('-')
			lastWasDash = true
		}
	}
	return strings.Trim(builder.String(), "-") + "-" + device.ID
}

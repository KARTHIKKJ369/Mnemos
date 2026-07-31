// Package storage creates and manages the PhotoVault filesystem layout.
package storage

import (
	"fmt"
	"os"
	"path/filepath"
)

// Layout contains absolute or relative paths for the PhotoVault storage roots.
type Layout struct {
	Root        string
	Blobs       string
	ByDevice    string
	Thumbnails  string
	Previews    string
	Locked      string
	LockedBlobs string
}

// Ensure creates the required PhotoVault filesystem layout.
func Ensure(root string) (Layout, error) {
	layout := Layout{
		Root:        root,
		Blobs:       filepath.Join(root, "blobs"),
		ByDevice:    filepath.Join(root, "blobs", "by-device"),
		Thumbnails:  filepath.Join(root, "derived", "thumbnails"),
		Previews:    filepath.Join(root, "derived", "previews"),
		Locked:      filepath.Join(root, ".locked"),
		LockedBlobs: filepath.Join(root, ".locked", "blobs"),
	}
	for _, directory := range []string{layout.ByDevice, layout.Thumbnails, layout.Previews, layout.LockedBlobs} {
		if err := os.MkdirAll(directory, 0o750); err != nil {
			return Layout{}, fmt.Errorf("create storage directory %s: %w", directory, err)
		}
	}
	return layout, nil
}

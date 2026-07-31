package processing

import (
	"context"
	"image"
	"image/color"
	"image/jpeg"
	"os"
	"path/filepath"
	"testing"

	"photovault/internal/files"
	"photovault/internal/storage"
)

func TestMediaProcessorGeneratesImageThumbnail(t *testing.T) {
	root := t.TempDir()
	layout, err := storage.Ensure(root)
	if err != nil {
		t.Fatalf("ensure storage: %v", err)
	}
	hash := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	relative := "blobs/by-device/test/2026/07/" + hash + ".jpg"
	absolute := filepath.Join(root, filepath.FromSlash(relative))
	if err := os.MkdirAll(filepath.Dir(absolute), 0o750); err != nil {
		t.Fatal(err)
	}
	source := image.NewRGBA(image.Rect(0, 0, 512, 128))
	source.Set(0, 0, color.White)
	output, err := os.Create(absolute)
	if err != nil {
		t.Fatal(err)
	}
	if err := jpeg.Encode(output, source, nil); err != nil {
		t.Fatal(err)
	}
	output.Close()
	processor := NewMediaProcessor(storage.NewBlobStore(layout), layout)
	result, err := processor.Process(context.Background(), files.File{ID: "file", Hash: hash, MIMEType: "image/jpeg", StoragePath: relative})
	if err != nil {
		t.Fatalf("process thumbnail: %v", err)
	}
	if result.ThumbnailPath != "thumbnails/"+hash+".jpg" || result.PreviewPath != "" {
		t.Fatalf("unexpected result: %+v", result)
	}
	generated, err := os.Open(filepath.Join(root, filepath.FromSlash(result.ThumbnailPath)))
	if err != nil {
		t.Fatal(err)
	}
	defer generated.Close()
	thumbnail, _, err := image.Decode(generated)
	if err != nil {
		t.Fatal(err)
	}
	if got := thumbnail.Bounds().Size(); got.X != 256 || got.Y != 64 {
		t.Fatalf("thumbnail size = %v, want 256x64", got)
	}
}

func TestMediaProcessorSkipsUnsupportedFiles(t *testing.T) {
	processor := NewMediaProcessor(storage.NewBlobStore(storage.Layout{}), storage.Layout{})
	if _, err := processor.Process(context.Background(), files.File{MIMEType: "application/pdf"}); err != ErrSkipped {
		t.Fatalf("error = %v, want ErrSkipped", err)
	}
}

package httpapi

import (
	"bytes"
	"context"
	"database/sql"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"photovault/internal/database"
	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/ratelimit"
	"photovault/internal/storage"
	"photovault/internal/synchronization"
	"photovault/internal/uploads"
)

const mediaHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

func TestMediaOriginal(t *testing.T) {
	router, token, db, root := newMediaRouter(t, []byte("original image bytes"))

	t.Run("successful download includes media metadata", func(t *testing.T) {
		response := executeMedia(router, token, "file-media", "")
		if response.Code != http.StatusOK || response.Body.String() != "original image bytes" {
			t.Fatalf("status = %d, body = %q", response.Code, response.Body.String())
		}
		if got := response.Header().Get("Content-Type"); got != "image/jpeg" {
			t.Fatalf("Content-Type = %q", got)
		}
		if got := response.Header().Get("Content-Length"); got != "20" {
			t.Fatalf("Content-Length = %q", got)
		}
		if got := response.Header().Get("ETag"); got != `"`+mediaHash+`"` {
			t.Fatalf("ETag = %q", got)
		}
		if got := response.Header().Get("Last-Modified"); got != "Fri, 31 Jul 2026 12:00:00 GMT" {
			t.Fatalf("Last-Modified = %q", got)
		}
	})

	t.Run("invalid ID", func(t *testing.T) {
		response := executeMedia(router, token, "missing", "")
		if response.Code != http.StatusNotFound {
			t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
		}
	})

	t.Run("unauthorized", func(t *testing.T) {
		response := executeMedia(router, "", "file-media", "")
		if response.Code != http.StatusUnauthorized {
			t.Fatalf("status = %d", response.Code)
		}
	})

	t.Run("range request", func(t *testing.T) {
		response := executeMedia(router, token, "file-media", "bytes=9-13")
		if response.Code != http.StatusPartialContent || response.Body.String() != "image" {
			t.Fatalf("status = %d, body = %q", response.Code, response.Body.String())
		}
		if got := response.Header().Get("Content-Range"); got != "bytes 9-13/20" {
			t.Fatalf("Content-Range = %q", got)
		}
	})

	t.Run("ETag conditional request", func(t *testing.T) {
		request := httptest.NewRequest(http.MethodGet, "/media/file-media/original", nil)
		request.Header.Set("Authorization", "Bearer "+token)
		request.Header.Set("If-None-Match", `"`+mediaHash+`"`)
		response := httptest.NewRecorder()
		router.ServeHTTP(response, request)
		if response.Code != http.StatusNotModified {
			t.Fatalf("status = %d", response.Code)
		}
	})

	t.Run("derived endpoints are unavailable before processing", func(t *testing.T) {
		response := executeMedia(router, token, "file-media", "")
		request := httptest.NewRequest(http.MethodGet, "/media/file-media/thumbnail", nil)
		request.Header.Set("Authorization", "Bearer "+token)
		missing := httptest.NewRecorder()
		router.ServeHTTP(missing, request)
		if response.Code != http.StatusOK || missing.Code != http.StatusNotFound {
			t.Fatalf("statuses = %d, %d", response.Code, missing.Code)
		}
	})

	t.Run("generated thumbnail and preview stream with authorization", func(t *testing.T) {
		thumbnailPath, previewPath := "thumbnails/"+mediaHash+".jpg", "previews/"+mediaHash+".mp4"
		if err := os.MkdirAll(filepath.Join(root, "thumbnails"), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.MkdirAll(filepath.Join(root, "previews"), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(root, filepath.FromSlash(thumbnailPath)), []byte("thumb"), 0o600); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(root, filepath.FromSlash(previewPath)), []byte("preview-content"), 0o600); err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec("UPDATE files SET thumbnail_path = ?, preview_path = ? WHERE id = 'file-media'", thumbnailPath, previewPath); err != nil {
			t.Fatal(err)
		}
		thumbnail := httptest.NewRecorder()
		thumbRequest := httptest.NewRequest(http.MethodGet, "/media/file-media/thumbnail", nil)
		thumbRequest.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(thumbnail, thumbRequest)
		if thumbnail.Code != http.StatusOK || thumbnail.Body.String() != "thumb" || thumbnail.Header().Get("Content-Type") != "image/jpeg" {
			t.Fatalf("thumbnail response = %d %q %q", thumbnail.Code, thumbnail.Body.String(), thumbnail.Header().Get("Content-Type"))
		}
		preview := httptest.NewRecorder()
		previewRequest := httptest.NewRequest(http.MethodGet, "/media/file-media/preview", nil)
		previewRequest.Header.Set("Authorization", "Bearer "+token)
		previewRequest.Header.Set("Range", "bytes=0-6")
		router.ServeHTTP(preview, previewRequest)
		if preview.Code != http.StatusPartialContent || preview.Body.String() != "preview" {
			t.Fatalf("preview response = %d %q", preview.Code, preview.Body.String())
		}
		unauthorized := httptest.NewRecorder()
		router.ServeHTTP(unauthorized, httptest.NewRequest(http.MethodGet, "/media/file-media/preview", nil))
		if unauthorized.Code != http.StatusUnauthorized {
			t.Fatalf("unauthorized = %d", unauthorized.Code)
		}
	})
}

func TestMediaOriginalStreamsLargeFile(t *testing.T) {
	content := bytes.Repeat([]byte("a"), 8<<20)
	router, token, _, _ := newMediaRouter(t, content)
	response := executeMedia(router, token, "file-media", "")
	if response.Code != http.StatusOK || response.Body.Len() != len(content) {
		t.Fatalf("status = %d, length = %d, want %d", response.Code, response.Body.Len(), len(content))
	}
	if !bytes.Equal(response.Body.Bytes(), content) {
		t.Fatal("large response body differs from blob")
	}
}

func newMediaRouter(t *testing.T, content []byte) (http.Handler, string, *sql.DB, string) {
	t.Helper()
	ctx := context.Background()
	root := t.TempDir()
	layout, err := storage.Ensure(root)
	if err != nil {
		t.Fatalf("prepare storage: %v", err)
	}
	db, err := database.Open(ctx, filepath.Join(root, "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	deviceService := devices.NewService(devices.NewSQLiteStore(db))
	registration, err := deviceService.Register(ctx, "Media Phone", "ios")
	if err != nil {
		t.Fatalf("register device: %v", err)
	}
	relativePath := "blobs/by-device/test/2026/07/" + mediaHash + ".jpg"
	absolutePath := filepath.Join(root, filepath.FromSlash(relativePath))
	if err := os.MkdirAll(filepath.Dir(absolutePath), 0o750); err != nil {
		t.Fatalf("create blob directory: %v", err)
	}
	if err := os.WriteFile(absolutePath, content, 0o600); err != nil {
		t.Fatalf("write blob: %v", err)
	}
	if _, err := db.Exec(`INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status) VALUES (?, ?, 'photo.jpg', 'image/jpeg', ?, ?, ?, ?, 'ready')`, "file-media", mediaHash, len(content), registration.Device.ID, relativePath, time.Date(2026, time.July, 31, 12, 0, 0, 0, time.UTC).UnixMilli()); err != nil {
		t.Fatalf("insert media metadata: %v", err)
	}
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	fileRepository := files.NewRepository(db)
	mediaHandler := NewMediaHandler(files.NewMediaService(fileRepository, storage.NewBlobStore(layout)), logger)
	syncRepository, err := synchronization.NewSyncRepository(ctx, db)
	if err != nil {
		t.Fatalf("create sync repository: %v", err)
	}
	t.Cleanup(func() { syncRepository.Close() })
	syncService, err := synchronization.NewSyncService(syncRepository, 100, 500, 250)
	if err != nil {
		t.Fatalf("create sync service: %v", err)
	}
	syncHandler := NewSyncHandler(syncService, logger)
	uploadHandler := uploads.NewHandler(storage.NewBlobStore(layout), fileRepository, 1<<20, layout.Blobs, logger)
	fileHandler := NewFileHandler(files.NewService(fileRepository, nil), logger)
	router := NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler, http.HandlerFunc(fileHandler.Exists), syncHandler, nil, mediaHandler)
	return router, registration.Token, db, root
}

func executeMedia(handler http.Handler, token, id, byteRange string) *httptest.ResponseRecorder {
	request := httptest.NewRequest(http.MethodGet, "/media/"+id+"/original", nil)
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	if byteRange != "" {
		request.Header.Set("Range", byteRange)
	}
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

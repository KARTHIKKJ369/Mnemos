package httpapi

import (
	"context"
	"database/sql"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"sync"
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

const existingHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

func TestFilesExistsEndpoint(t *testing.T) {
	router, token := newFilesRouter(t)

	t.Run("missing authentication", func(t *testing.T) {
		response := executeExists(router, "", existingHash)
		if response.Code != http.StatusUnauthorized {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
		}
	})
	t.Run("malformed hash", func(t *testing.T) {
		response := executeExists(router, token, "invalid")
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("uppercase hash", func(t *testing.T) {
		response := executeExists(router, token, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
		assertExistenceResponse(t, response, true)
	})
	t.Run("existing hash", func(t *testing.T) {
		response := executeExists(router, token, existingHash)
		assertExistenceResponse(t, response, true)
	})
	t.Run("nonexistent hash", func(t *testing.T) {
		response := executeExists(router, token, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
		assertExistenceResponse(t, response, false)
	})
}

func TestFilesExistsHandlesConcurrentRequests(t *testing.T) {
	router, token := newFilesRouter(t)
	const requests = 20
	responses := make(chan *httptest.ResponseRecorder, requests)
	var group sync.WaitGroup
	for range requests {
		group.Add(1)
		go func() {
			defer group.Done()
			responses <- executeExists(router, token, existingHash)
		}()
	}
	group.Wait()
	close(responses)
	for response := range responses {
		assertExistenceResponse(t, response, true)
	}
}

func newFilesRouter(t *testing.T) (http.Handler, string) {
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
	registration, err := deviceService.Register(ctx, "Test Phone", "ios")
	if err != nil {
		t.Fatalf("register device: %v", err)
	}
	insertFile(t, db, registration.Device.ID)
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	fileService := files.NewService(files.NewRepository(db), files.NewLRUExistenceCache(100, time.Minute))
	fileHandler := NewFileHandler(fileService, logger)
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
	uploadHandler := uploads.NewHandler(storage.NewBlobStore(layout), files.NewRepository(db), 1<<20, layout.Blobs, logger)
	router := NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler, http.HandlerFunc(fileHandler.Exists), syncHandler, nil)
	return router, registration.Token
}

func insertFile(t *testing.T, db *sql.DB, deviceID string) {
	t.Helper()
	if _, err := db.Exec("INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status) VALUES ('file-1', ?, 'photo.jpg', 'image/jpeg', 12345, ?, 'blobs/test', 0, 'ready')", existingHash, deviceID); err != nil {
		t.Fatalf("insert test file: %v", err)
	}
}

func executeExists(handler http.Handler, token, hash string) *httptest.ResponseRecorder {
	request := httptest.NewRequest(http.MethodGet, "/files/exists?hash="+hash, nil)
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

func assertExistenceResponse(t *testing.T, response *httptest.ResponseRecorder, exists bool) {
	t.Helper()
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body["exists"] != exists {
		t.Fatalf("exists = %v, want %t", body["exists"], exists)
	}
	if exists && (body["file_id"] != "file-1" || body["size_bytes"] != float64(12345)) {
		t.Fatalf("existing response exposed incorrect metadata: %v", body)
	}
	if !exists && len(body) != 1 {
		t.Fatalf("nonexistent response exposed extra metadata: %v", body)
	}
}

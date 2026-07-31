package httpapi

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"photovault/internal/database"
	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/ratelimit"
	"photovault/internal/storage"
	"photovault/internal/synchronization"
	"photovault/internal/uploads"
)

const testFileID = "00000000-0000-0000-0000-0000000000aa"

func TestSyncDiffEndpoint(t *testing.T) {
	router, tokens := newSyncRouter(t)

	t.Run("missing authentication", func(t *testing.T) {
		response := executeSyncDiff(router, "", "")
		if response.Code != http.StatusUnauthorized {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
		}
	})
	t.Run("invalid since", func(t *testing.T) {
		response := executeSyncDiff(router, tokens.deviceB, "since=-1")
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("invalid limit", func(t *testing.T) {
		response := executeSyncDiff(router, tokens.deviceB, "limit=0")
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("returns unsynchronized files", func(t *testing.T) {
		response := executeSyncDiff(router, tokens.deviceB, "")
		assertDiffResponse(t, response, 1, testFileID)
	})
	t.Run("respects since cursor", func(t *testing.T) {
		first := executeSyncDiff(router, tokens.deviceB, "")
		var body map[string]any
		if err := json.Unmarshal(first.Body.Bytes(), &body); err != nil {
			t.Fatalf("decode first diff: %v", err)
		}
		nextSince, ok := body["next_since"].(float64)
		if !ok {
			t.Fatalf("next_since missing: %v", body)
		}
		second := executeSyncDiff(router, tokens.deviceB, "since="+formatInt(int64(nextSince)))
		assertDiffResponse(t, second, 0, "")
	})
}

func TestSyncAckEndpoint(t *testing.T) {
	router, tokens := newSyncRouter(t)

	t.Run("missing authentication", func(t *testing.T) {
		response := executeSyncAck(router, "", []string{"00000000-0000-0000-0000-000000000001"})
		if response.Code != http.StatusUnauthorized {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
		}
	})
	t.Run("empty batch", func(t *testing.T) {
		response := executeSyncAck(router, tokens.deviceB, nil)
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("duplicate file ids", func(t *testing.T) {
		response := executeSyncAck(router, tokens.deviceB, []string{
			"00000000-0000-0000-0000-000000000099",
			"00000000-0000-0000-0000-000000000099",
		})
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("invalid uuid", func(t *testing.T) {
		response := executeSyncAck(router, tokens.deviceB, []string{"not-a-uuid"})
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("unknown file id", func(t *testing.T) {
		response := executeSyncAck(router, tokens.deviceB, []string{"00000000-0000-0000-0000-000000000099"})
		if response.Code != http.StatusBadRequest {
			t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
		}
	})
	t.Run("successful ack", func(t *testing.T) {
		response := executeSyncAck(router, tokens.deviceB, []string{testFileID})
		if response.Code != http.StatusOK {
			t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
		}
		var body map[string]any
		if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
			t.Fatalf("decode ack response: %v", err)
		}
		if body["acknowledged"] != float64(1) {
			t.Fatalf("acknowledged = %v, want 1", body["acknowledged"])
		}
	})
}

func TestSyncIntegrationTwoDevices(t *testing.T) {
	root := t.TempDir()
	layout, err := storage.Ensure(root)
	if err != nil {
		t.Fatalf("prepare storage: %v", err)
	}
	ctx := context.Background()
	db, err := database.Open(ctx, filepath.Join(root, "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	deviceService := devices.NewService(devices.NewSQLiteStore(db))
	registrationA, err := deviceService.Register(ctx, "Device A", "ios")
	if err != nil {
		t.Fatalf("register device a: %v", err)
	}
	registrationB, err := deviceService.Register(ctx, "Device B", "mac")
	if err != nil {
		t.Fatalf("register device b: %v", err)
	}

	fileRepository := files.NewRepository(db)
	uploadHandler := uploads.NewHandler(storage.NewBlobStore(layout), fileRepository, 1<<20, layout.Blobs, slog.New(slog.NewJSONHandler(io.Discard, nil)))
	syncRepository, err := synchronization.NewSyncRepository(ctx, db)
	if err != nil {
		t.Fatalf("create sync repository: %v", err)
	}
	t.Cleanup(func() { syncRepository.Close() })
	syncService, err := synchronization.NewSyncService(syncRepository, 100, 500, 250)
	if err != nil {
		t.Fatalf("create sync service: %v", err)
	}
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	fileHandler := NewFileHandler(files.NewService(fileRepository, files.NoopExistenceCache{}), logger)
	syncHandler := NewSyncHandler(syncService, logger)
	router := NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler, http.HandlerFunc(fileHandler.Exists), syncHandler)

	data := []byte("integration upload bytes")
	uploadResponse := uploadViaRouter(t, router, registrationA.Token, "photo.jpg", data)
	if uploadResponse.Code != http.StatusCreated {
		t.Fatalf("upload status = %d, body = %s", uploadResponse.Code, uploadResponse.Body.String())
	}
	var uploaded map[string]any
	if err := json.Unmarshal(uploadResponse.Body.Bytes(), &uploaded); err != nil {
		t.Fatalf("decode upload response: %v", err)
	}
	fileID, ok := uploaded["file_id"].(string)
	if !ok || fileID == "" {
		t.Fatalf("upload response missing file_id: %v", uploaded)
	}

	diffResponse := executeSyncDiff(router, registrationB.Token, "")
	assertDiffResponse(t, diffResponse, 1, fileID)

	ackResponse := executeSyncAck(router, registrationB.Token, []string{fileID})
	if ackResponse.Code != http.StatusOK {
		t.Fatalf("ack status = %d, body = %s", ackResponse.Code, ackResponse.Body.String())
	}

	secondDiff := executeSyncDiff(router, registrationB.Token, "")
	assertDiffResponse(t, secondDiff, 0, "")

	retryAck := executeSyncAck(router, registrationB.Token, []string{fileID})
	if retryAck.Code != http.StatusOK {
		t.Fatalf("retry ack status = %d, body = %s", retryAck.Code, retryAck.Body.String())
	}
	var retryBody map[string]any
	if err := json.Unmarshal(retryAck.Body.Bytes(), &retryBody); err != nil {
		t.Fatalf("decode retry ack: %v", err)
	}
	if retryBody["acknowledged"] != float64(1) {
		t.Fatalf("retry acknowledged = %v, want 1", retryBody["acknowledged"])
	}
}

type syncTokens struct {
	deviceA string
	deviceB string
}

func newSyncRouter(t *testing.T) (http.Handler, syncTokens) {
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
	registrationA, err := deviceService.Register(ctx, "Device A", "ios")
	if err != nil {
		t.Fatalf("register device a: %v", err)
	}
	registrationB, err := deviceService.Register(ctx, "Device B", "mac")
	if err != nil {
		t.Fatalf("register device b: %v", err)
	}
	if _, err := db.Exec(`
		INSERT INTO files (id, hash, original_filename, mime_type, size_bytes, uploaded_by_device_id, storage_path, uploaded_at, status)
		VALUES (?, ?, 'photo.jpg', 'image/jpeg', 12345, ?, 'blobs/test', 100, 'ready')
	`, testFileID, repeatHash('a'), registrationA.Device.ID); err != nil {
		t.Fatalf("insert file: %v", err)
	}
	if _, err := db.Exec("INSERT INTO file_sync_state (device_id, file_id, synced_at) VALUES (?, ?, 100)", registrationA.Device.ID, testFileID); err != nil {
		t.Fatalf("insert sync state: %v", err)
	}

	syncRepository, err := synchronization.NewSyncRepository(ctx, db)
	if err != nil {
		t.Fatalf("create sync repository: %v", err)
	}
	t.Cleanup(func() { syncRepository.Close() })
	syncService, err := synchronization.NewSyncService(syncRepository, 100, 500, 250)
	if err != nil {
		t.Fatalf("create sync service: %v", err)
	}
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	fileHandler := NewFileHandler(files.NewService(files.NewRepository(db), files.NoopExistenceCache{}), logger)
	syncHandler := NewSyncHandler(syncService, logger)
	router := NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploads.NewHandler(storage.NewBlobStore(layout), files.NewRepository(db), 1<<20, layout.Blobs, logger), http.HandlerFunc(fileHandler.Exists), syncHandler)
	return router, syncTokens{deviceA: registrationA.Token, deviceB: registrationB.Token}
}

func executeSyncDiff(handler http.Handler, token, query string) *httptest.ResponseRecorder {
	path := "/sync/diff"
	if query != "" {
		path += "?" + query
	}
	request := httptest.NewRequest(http.MethodGet, path, nil)
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

func executeSyncAck(handler http.Handler, token string, fileIDs []string) *httptest.ResponseRecorder {
	body, err := json.Marshal(map[string][]string{"file_ids": fileIDs})
	if err != nil {
		panic(err)
	}
	request := httptest.NewRequest(http.MethodPost, "/sync/ack", bytes.NewReader(body))
	request.Header.Set("Content-Type", "application/json")
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

func uploadViaRouter(t *testing.T, handler http.Handler, token, filename string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("file", filename)
	if err != nil {
		t.Fatalf("create form file: %v", err)
	}
	if _, err := part.Write(data); err != nil {
		t.Fatalf("write form file: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}
	request := httptest.NewRequest(http.MethodPost, "/upload", &body)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	request.Header.Set("Authorization", "Bearer "+token)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

func assertDiffResponse(t *testing.T, response *httptest.ResponseRecorder, wantCount int, wantFileID string) {
	t.Helper()
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	var body map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode diff response: %v", err)
	}
	files, ok := body["files"].([]any)
	if !ok {
		t.Fatalf("files missing: %v", body)
	}
	if len(files) != wantCount {
		t.Fatalf("file count = %d, want %d", len(files), wantCount)
	}
	if wantCount == 0 {
		if _, exists := body["next_since"]; exists {
			t.Fatalf("empty diff should not include next_since: %v", body)
		}
		return
	}
	first, ok := files[0].(map[string]any)
	if !ok {
		t.Fatalf("first file has unexpected shape: %v", files[0])
	}
	if first["file_id"] != wantFileID {
		t.Fatalf("file_id = %v, want %s", first["file_id"], wantFileID)
	}
	for _, key := range []string{"hash", "filename", "mime_type", "size_bytes", "thumbnail_available", "preview_available", "uploaded_at"} {
		if _, exists := first[key]; !exists {
			t.Fatalf("missing metadata key %s: %v", key, first)
		}
	}
	if _, exists := first["storage_path"]; exists {
		t.Fatalf("diff exposed storage_path: %v", first)
	}
	if body["next_since"] == nil {
		t.Fatalf("expected next_since for non-empty diff: %v", body)
	}
}

func repeatHash(character byte) string {
	value := bytes.Repeat([]byte{character}, 32)
	sum := sha256.Sum256(value)
	return hex.EncodeToString(sum[:])
}

func formatInt(value int64) string {
	return jsonNumber(value)
}

func jsonNumber(value int64) string {
	buffer, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	return string(buffer)
}

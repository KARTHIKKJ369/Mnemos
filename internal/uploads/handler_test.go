package uploads

import (
	"bytes"
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"photovault/internal/authn"
	"photovault/internal/database"
	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/storage"
)

func TestSuccessfulUploadCreatesBlobAndSyncState(t *testing.T) {
	handler, db, device := newTestHandler(t, 1<<20)
	data := []byte{0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 'J', 'F', 'I', 'F'}
	response := upload(t, handler, device, "photo.jpg", data)

	if response.Code != http.StatusCreated {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusCreated)
	}
	result := decodeUploadResponse(t, response)
	wantHash := sha256.Sum256(data)
	if result.Hash != hex.EncodeToString(wantHash[:]) {
		t.Fatalf("hash = %s, want %x", result.Hash, wantHash)
	}
	if result.MIMEType != "image/jpeg" || result.SizeBytes != int64(len(data)) || result.Deduplicated {
		t.Fatalf("unexpected upload response: %+v", result)
	}

	var fileCount, syncCount int
	if err := db.QueryRow("SELECT COUNT(*) FROM files WHERE hash = ?", result.Hash).Scan(&fileCount); err != nil {
		t.Fatalf("count file rows: %v", err)
	}
	if err := db.QueryRow("SELECT COUNT(*) FROM file_sync_state WHERE device_id = ? AND file_id = ?", device.ID, result.FileID).Scan(&syncCount); err != nil {
		t.Fatalf("count sync rows: %v", err)
	}
	if fileCount != 1 || syncCount != 1 {
		t.Fatalf("file_count = %d, sync_count = %d, want 1 each", fileCount, syncCount)
	}
}

func TestDuplicateUploadReusesFile(t *testing.T) {
	handler, db, device := newTestHandler(t, 1<<20)
	data := []byte("same upload bytes")
	first := decodeUploadResponse(t, upload(t, handler, device, "one.bin", data))
	second := decodeUploadResponse(t, upload(t, handler, device, "two.bin", data))
	if !second.Deduplicated || second.FileID != first.FileID {
		t.Fatalf("duplicate response = %+v, want original file ID and deduplicated=true", second)
	}
	var count int
	if err := db.QueryRow("SELECT COUNT(*) FROM files").Scan(&count); err != nil {
		t.Fatalf("count file rows: %v", err)
	}
	if count != 1 {
		t.Fatalf("file row count = %d, want 1", count)
	}
}

func TestConcurrentDuplicateUploadsCreateOneFile(t *testing.T) {
	handler, db, device := newTestHandler(t, 1<<20)
	data := []byte("concurrent upload bytes")
	responses := make(chan *httptest.ResponseRecorder, 2)
	for range 2 {
		request := newUploadRequest(t, "file.bin", data)
		go func(request *http.Request) {
			response := httptest.NewRecorder()
			authenticatedHandler(handler, device).ServeHTTP(response, request)
			responses <- response
		}(request)
	}
	for range 2 {
		response := <-responses
		if response.Code != http.StatusCreated {
			t.Errorf("status code = %d, want %d", response.Code, http.StatusCreated)
		}
	}
	var count int
	if err := db.QueryRow("SELECT COUNT(*) FROM files").Scan(&count); err != nil {
		t.Fatalf("count file rows: %v", err)
	}
	if count != 1 {
		t.Fatalf("file row count = %d, want 1", count)
	}
}

func TestUploadRejectsOverLimitAndInvalidMultipart(t *testing.T) {
	handler, _, device := newTestHandler(t, 64)
	overLimit := upload(t, handler, device, "large.bin", bytes.Repeat([]byte("a"), 1024))
	if overLimit.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("over-limit status = %d, want %d", overLimit.Code, http.StatusRequestEntityTooLarge)
	}

	request := httptest.NewRequest(http.MethodPost, "/upload", bytes.NewBufferString("not multipart"))
	request.Header.Set("Authorization", "Bearer test")
	response := httptest.NewRecorder()
	authenticatedHandler(handler, device).ServeHTTP(response, request)
	if response.Code != http.StatusBadRequest {
		t.Fatalf("invalid multipart status = %d, want %d", response.Code, http.StatusBadRequest)
	}
}

func TestUploadRequiresAuthentication(t *testing.T) {
	handler, _, _ := newTestHandler(t, 1<<20)
	request := httptest.NewRequest(http.MethodPost, "/upload", nil)
	response := httptest.NewRecorder()
	authenticatedHandler(handler, devices.Device{ID: "device"}).ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusUnauthorized)
	}
}

type uploadResponse struct {
	FileID       string `json:"file_id"`
	Hash         string `json:"hash"`
	SizeBytes    int64  `json:"size_bytes"`
	MIMEType     string `json:"mime_type"`
	Deduplicated bool   `json:"deduplicated"`
}

func decodeUploadResponse(t *testing.T, response *httptest.ResponseRecorder) uploadResponse {
	t.Helper()
	if response.Code != http.StatusCreated {
		t.Fatalf("status code = %d, body = %s", response.Code, response.Body.String())
	}
	var result uploadResponse
	if err := json.Unmarshal(response.Body.Bytes(), &result); err != nil {
		t.Fatalf("decode upload response: %v", err)
	}
	return result
}

func upload(t *testing.T, handler http.Handler, device devices.Device, filename string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	request := newUploadRequest(t, filename, data)
	response := httptest.NewRecorder()
	authenticatedHandler(handler, device).ServeHTTP(response, request)
	return response
}

func newUploadRequest(t *testing.T, filename string, data []byte) *http.Request {
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
	request.Header.Set("Authorization", "Bearer test")
	return request
}

func authenticatedHandler(handler http.Handler, device devices.Device) http.Handler {
	return authn.Middleware(slog.New(slog.NewJSONHandler(io.Discard, nil)), testAuthenticator{device: device})(handler)
}

func newTestHandler(t *testing.T, maximumSize int64) (http.Handler, *sql.DB, devices.Device) {
	t.Helper()
	root := t.TempDir()
	layout, err := storage.Ensure(root)
	if err != nil {
		t.Fatalf("prepare storage: %v", err)
	}
	db, err := database.Open(context.Background(), filepath.Join(root, "vault.db"))
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	device := devices.Device{ID: "device-1", Name: "Test Phone", DeviceType: "ios"}
	if _, err := db.Exec("INSERT INTO devices (id, name, device_type, auth_token_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)", device.ID, device.Name, device.DeviceType, "hash", 0, 0); err != nil {
		t.Fatalf("insert device: %v", err)
	}
	return NewHandler(storage.NewBlobStore(layout), files.NewRepository(db), maximumSize, layout.Blobs, slog.New(slog.NewJSONHandler(io.Discard, nil))), db, device
}

type testAuthenticator struct{ device devices.Device }

func (authenticator testAuthenticator) Authenticate(context.Context, string) (devices.Device, error) {
	return authenticator.device, nil
}

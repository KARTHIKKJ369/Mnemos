package httpapi

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"photovault/internal/devices"
	"photovault/internal/synchronization"
)

func TestHealth(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	response := httptest.NewRecorder()
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))

	NewRouter(logger, nil, allowAllRegistrations{}, nil, http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger), nil).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusOK)
	}
	if body := response.Body.String(); body != "{\"status\":\"ok\"}\n" {
		t.Fatalf("body = %q, want health response", body)
	}
}

type allowAllRegistrations struct{}

func (allowAllRegistrations) Allow(string) bool { return true }

type noopSyncCoordinator struct{}

func (noopSyncCoordinator) Diff(context.Context, string, *int64, *int) (synchronization.Diff, error) {
	return synchronization.Diff{}, nil
}

func (noopSyncCoordinator) Ack(context.Context, string, []string) (int, error) {
	return 0, nil
}

type mockDeviceService struct {
	devList []devices.DeviceSummary
}

func (m *mockDeviceService) Register(context.Context, string, string) (devices.Registration, error) {
	return devices.Registration{}, nil
}

func (m *mockDeviceService) List(context.Context) ([]devices.DeviceSummary, error) {
	return m.devList, nil
}

func (m *mockDeviceService) Authenticate(_ context.Context, token string) (devices.Device, error) {
	if token == "valid-token" {
		return devices.Device{ID: "dev-1", Name: "My Phone", DeviceType: "ios"}, nil
	}
	return devices.Device{}, devices.ErrInvalidToken
}

func (m *mockDeviceService) AdminRegistration() (devices.Registration, bool) {
	return devices.Registration{
		Device: devices.Device{ID: "admin-1", Name: "Server Host (Admin)", DeviceType: "mac"},
		Token:  "admin-token-123",
	}, true
}

func TestListDevicesEndpoint(t *testing.T) {
	t.Parallel()

	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	mockDevs := &mockDeviceService{
		devList: []devices.DeviceSummary{
			{ID: "dev-1", Name: "iPhone", DeviceType: "ios", CreatedAt: time.Now(), LastSeenAt: time.Now()},
		},
	}

	router := NewRouter(logger, mockDevs, allowAllRegistrations{}, mockDevs, http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger), nil)

	// Request without auth
	reqUnauth := httptest.NewRequest(http.MethodGet, "/devices", nil)
	recUnauth := httptest.NewRecorder()
	router.ServeHTTP(recUnauth, reqUnauth)
	if recUnauth.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", recUnauth.Code)
	}

	// Request with valid auth
	reqAuth := httptest.NewRequest(http.MethodGet, "/devices", nil)
	reqAuth.Header.Set("Authorization", "Bearer valid-token")
	recAuth := httptest.NewRecorder()
	router.ServeHTTP(recAuth, reqAuth)
	if recAuth.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", recAuth.Code, recAuth.Body.String())
	}
}

func TestAuthBootstrapLocalhost(t *testing.T) {
	t.Parallel()

	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	mockDevs := &mockDeviceService{}
	router := NewRouter(logger, mockDevs, allowAllRegistrations{}, mockDevs, http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger), nil)

	req := httptest.NewRequest(http.MethodGet, "/auth/bootstrap", nil)
	req.RemoteAddr = "127.0.0.1:1234"
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), `"is_admin":true`) {
		t.Fatalf("expected is_admin:true, got %s", rec.Body.String())
	}
}

func TestAuthBootstrapRemote(t *testing.T) {
	t.Parallel()

	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	mockDevs := &mockDeviceService{}
	router := NewRouter(logger, mockDevs, allowAllRegistrations{}, mockDevs, http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger), nil)

	req := httptest.NewRequest(http.MethodGet, "/auth/bootstrap", nil)
	req.RemoteAddr = "100.64.0.5:1234"
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), `"is_admin":false`) {
		t.Fatalf("expected is_admin:false, got %s", rec.Body.String())
	}
}

func TestStorageConfigEndpoints(t *testing.T) {
	t.Parallel()

	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))
	mockDevs := &mockDeviceService{}
	storageHandler := NewStorageHandler(nil, mockDevs, logger, "/default/storage")

	router := NewRouter(logger, mockDevs, allowAllRegistrations{}, mockDevs,
		http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger),
		nil, nil, nil, nil, storageHandler,
	)

	// 1. Unauthorized GET
	req := httptest.NewRequest(http.MethodGet, "/storage/config", nil)
	req.RemoteAddr = "127.0.0.1:1234"
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 unauthorized, got %d", rec.Code)
	}

	// 2. Authorized GET
	req = httptest.NewRequest(http.MethodGet, "/storage/config", nil)
	req.RemoteAddr = "127.0.0.1:1234"
	req.Header.Set("Authorization", "Bearer valid-token")
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d, body: %s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), `"storage_path"`) {
		t.Fatalf("expected storage_path in json response, got %s", rec.Body.String())
	}

	// 3. Authorized POST with empty path
	req = httptest.NewRequest(http.MethodPost, "/storage/config", strings.NewReader(`{"storage_path":""}`))
	req.RemoteAddr = "127.0.0.1:1234"
	req.Header.Set("Authorization", "Bearer valid-token")
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for empty path, got %d", rec.Code)
	}

	tmpDir := t.TempDir()
	storageHandler.SetEnvPath(filepath.Join(tmpDir, ".env"))

	// 4. Authorized POST with valid directory
	req = httptest.NewRequest(http.MethodPost, "/storage/config", strings.NewReader(`{"storage_path":"`+tmpDir+`"}`))
	req.RemoteAddr = "127.0.0.1:1234"
	req.Header.Set("Authorization", "Bearer valid-token")
	rec = httptest.NewRecorder()
	router.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for valid path, got %d, body: %s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), `"status":"saved"`) {
		t.Fatalf("expected saved status in json response, got %s", rec.Body.String())
	}
}


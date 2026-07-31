package httpapi

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"photovault/internal/synchronization"
)

func TestHealth(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	response := httptest.NewRecorder()
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))

	NewRouter(logger, nil, allowAllRegistrations{}, nil, http.NotFoundHandler(), http.NotFoundHandler(), NewSyncHandler(&noopSyncCoordinator{}, logger)).ServeHTTP(response, request)

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

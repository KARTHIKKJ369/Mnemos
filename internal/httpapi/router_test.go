package httpapi

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealth(t *testing.T) {
	t.Parallel()

	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	response := httptest.NewRecorder()
	logger := slog.New(slog.NewJSONHandler(io.Discard, nil))

	NewRouter(logger, nil, allowAllRegistrations{}, nil, http.NotFoundHandler()).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusOK)
	}
	if body := response.Body.String(); body != "{\"status\":\"ok\"}\n" {
		t.Fatalf("body = %q, want health response", body)
	}
}

type allowAllRegistrations struct{}

func (allowAllRegistrations) Allow(string) bool { return true }

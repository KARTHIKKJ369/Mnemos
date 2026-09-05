package authn

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"photovault/internal/devices"
)

func TestMiddlewareAddsAuthenticatedDeviceToContext(t *testing.T) {
	t.Parallel()

	authenticator := testAuthenticator{device: devices.Device{ID: "device-1", Name: "Phone", DeviceType: "ios"}}
	handler := Middleware(testLogger(), authenticator)(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		device, ok := DeviceFromContext(request.Context())
		if !ok || device.ID != "device-1" {
			t.Fatal("authenticated device was not available in request context")
		}
		writer.WriteHeader(http.StatusNoContent)
	}))

	request := httptest.NewRequest(http.MethodGet, "/protected", nil)
	request.Header.Set("Authorization", "Bearer token-value")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)

	if response.Code != http.StatusNoContent {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusNoContent)
	}
}

func TestMiddlewareRejectsInvalidToken(t *testing.T) {
	t.Parallel()

	handler := Middleware(testLogger(), testAuthenticator{err: devices.ErrInvalidToken})(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		t.Fatal("next handler was called")
	}))
	request := httptest.NewRequest(http.MethodGet, "/protected", nil)
	request.Header.Set("Authorization", "Bearer invalid")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusUnauthorized)
	}
	if response.Header().Get("WWW-Authenticate") != "Bearer" {
		t.Fatal("WWW-Authenticate header is missing")
	}
}

func TestMiddlewareRejectsMalformedAuthorizationHeaders(t *testing.T) {
	t.Parallel()

	for _, header := range []string{"", "Token value", "Bearer", "Bearer    ", "Bearer token extra"} {
		header := header
		t.Run(header, func(t *testing.T) {
			handler := Middleware(testLogger(), testAuthenticator{})(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
				t.Fatal("next handler was called")
			}))
			request := httptest.NewRequest(http.MethodGet, "/protected", nil)
			request.Header.Set("Authorization", header)
			response := httptest.NewRecorder()
			handler.ServeHTTP(response, request)
			if response.Code != http.StatusUnauthorized {
				t.Fatalf("status code = %d, want %d", response.Code, http.StatusUnauthorized)
			}
		})
	}
}

func TestMiddlewareAcceptsLowercaseBearerPrefix(t *testing.T) {
	t.Parallel()

	handler := Middleware(testLogger(), testAuthenticator{device: devices.Device{ID: "device-1"}})(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	}))
	request := httptest.NewRequest(http.MethodGet, "/protected", nil)
	request.Header.Set("Authorization", "bearer token-value")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusNoContent {
		t.Fatalf("status code = %d, want %d", response.Code, http.StatusNoContent)
	}
}

func TestMiddlewareAcceptsQueryToken(t *testing.T) {
	t.Parallel()

	handler := Middleware(testLogger(), testAuthenticator{device: devices.Device{ID: "device-1"}})(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	}))

	for _, path := range []string{"/protected?token=token-value", "/protected?_t=token-value"} {
		request := httptest.NewRequest(http.MethodGet, path, nil)
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, request)
		if response.Code != http.StatusNoContent {
			t.Fatalf("path %s: status code = %d, want %d", path, response.Code, http.StatusNoContent)
		}
	}
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

type testAuthenticator struct {
	device devices.Device
	err    error
}

func (authenticator testAuthenticator) Authenticate(_ context.Context, _ string) (devices.Device, error) {
	if authenticator.err != nil {
		return devices.Device{}, authenticator.err
	}
	if authenticator.device.ID == "" {
		return devices.Device{}, errors.New("missing test device")
	}
	return authenticator.device, nil
}

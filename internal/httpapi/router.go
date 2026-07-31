// Package httpapi provides the HTTP API router and handlers.
package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"

	"photovault/internal/authn"
	"photovault/internal/devices"
	"photovault/internal/httperror"
)

const maxRegistrationRequestBytes = 1 << 20

// DeviceRegistrar registers devices and issues their bearer tokens.
type DeviceRegistrar interface {
	Register(ctx context.Context, name, deviceType string) (devices.Registration, error)
}

// RegistrationLimiter applies registration limits for remote IP addresses.
type RegistrationLimiter interface {
	Allow(remoteIP string) bool
}

// NewRouter builds the HTTP router for the PhotoVault API.
func NewRouter(logger *slog.Logger, deviceRegistrar DeviceRegistrar, registrationLimiter RegistrationLimiter, authenticator authn.Authenticator, uploadHandler, fileHandler http.Handler, syncHandler *SyncHandler, mediaHandlers ...http.Handler) http.Handler {
	var metrics interface {
		Observe(string, string, int, time.Duration)
	}
	if len(mediaHandlers) > 2 {
		if ops, ok := mediaHandlers[2].(*OperationsHandler); ok {
			metrics = ops.metrics
		}
	}
	router := chi.NewRouter()
	router.Use(middleware.RequestID)
	router.Use(middleware.RealIP)
	router.Use(requestLogger(logger, metrics))
	router.Use(middleware.Recoverer)
	if metrics == nil {
		router.Get("/health", health)
	}
	router.Post("/devices/register", registerDevice(deviceRegistrar, registrationLimiter))
	auth := authn.Middleware(logger, authenticator)
	router.With(auth).Post("/upload", uploadHandler.ServeHTTP)
	router.With(auth).Get("/files/exists", fileHandler.ServeHTTP)
	router.With(auth).Get("/sync/diff", syncHandler.Diff)
	router.With(auth).Post("/sync/ack", syncHandler.Ack)
	if len(mediaHandlers) > 0 && mediaHandlers[0] != nil {
		router.With(auth).Get("/media/{id}/original", mediaHandlers[0].ServeHTTP)
		if media, ok := mediaHandlers[0].(*MediaHandler); ok {
			router.With(auth).Get("/media/{id}/thumbnail", media.Thumbnail)
			router.With(auth).Get("/media/{id}/preview", media.Preview)
		}
	}
	if len(mediaHandlers) > 1 {
		if index, ok := mediaHandlers[1].(*IndexHandler); ok {
			router.With(auth).Get("/media", index.Search)
			router.With(auth).Get("/media/{id}", index.Get)
			router.With(auth).Post("/media/{id}/favorite", index.Favorite)
			router.With(auth).Delete("/media/{id}/favorite", index.Favorite)
			router.With(auth).Delete("/media/{id}", index.Delete)
		}
	}
	if len(mediaHandlers) > 2 {
		if ops, ok := mediaHandlers[2].(*OperationsHandler); ok {
			router.Get("/metrics", ops.Metrics)
			router.Get("/ready", ops.Ready)
			router.Get("/health", ops.Health)
		}
	}
	if len(mediaHandlers) > 3 {
		if vault, ok := mediaHandlers[3].(*VaultHandler); ok {
			router.With(auth).Post("/vaults", vault.Create)
		}
	}
	return router
}

func registerDevice(registrar DeviceRegistrar, limiter RegistrationLimiter) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		if !limiter.Allow(requestRemoteIP(request)) {
			writeError(writer, http.StatusTooManyRequests, "rate_limited", "too many registrations from this IP address")
			return
		}
		request.Body = http.MaxBytesReader(writer, request.Body, maxRegistrationRequestBytes)
		defer request.Body.Close()

		var input struct {
			Name       string `json:"name"`
			DeviceType string `json:"device_type"`
		}
		decoder := json.NewDecoder(request.Body)
		decoder.DisallowUnknownFields()
		if err := decoder.Decode(&input); err != nil {
			writeError(writer, http.StatusBadRequest, "invalid_request", "request body must be valid JSON")
			return
		}
		if err := ensureSingleJSONValue(decoder); err != nil {
			writeError(writer, http.StatusBadRequest, "invalid_request", "request body must contain one JSON object")
			return
		}

		registration, err := registrar.Register(request.Context(), input.Name, input.DeviceType)
		if err != nil {
			if errors.Is(err, devices.ErrInvalidDeviceName) || errors.Is(err, devices.ErrInvalidDeviceType) {
				writeError(writer, http.StatusBadRequest, "invalid_request", "name must be 1 to 100 characters and device_type must be ios, android, mac, or web")
				return
			}
			writeError(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
			return
		}

		writeJSON(writer, http.StatusCreated, map[string]string{
			"device_id":  registration.Device.ID,
			"auth_token": registration.Token,
		})
	}
}

func ensureSingleJSONValue(decoder *json.Decoder) error {
	var extra any
	if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
		return errors.New("request contains multiple JSON values")
	}
	return nil
}

func health(writer http.ResponseWriter, request *http.Request) {
	writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
}

func requestLogger(logger *slog.Logger, metrics interface {
	Observe(string, string, int, time.Duration)
}) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
			startedAt := time.Now()
			recorder := &statusRecorder{ResponseWriter: writer, status: http.StatusOK}
			next.ServeHTTP(recorder, request)
			route := request.URL.Path
			if routeContext := chi.RouteContext(request.Context()); routeContext != nil && routeContext.RoutePattern() != "" {
				route = routeContext.RoutePattern()
			}
			if metrics != nil {
				metrics.Observe(request.Method, route, recorder.status, time.Since(startedAt))
			}
			logger.Info("HTTP request completed",
				"request_id", middleware.GetReqID(request.Context()),
				"method", request.Method,
				"path", request.URL.Path,
				"duration_ms", time.Since(startedAt).Milliseconds(),
			)
		})
	}
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

func writeJSON(writer http.ResponseWriter, status int, response any) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(response)
}

func writeError(writer http.ResponseWriter, status int, code, message string) {
	httperror.Write(writer, status, code, message)
}

func requestRemoteIP(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err == nil {
		return host
	}
	return request.RemoteAddr
}

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

// DeviceLister lists registered devices.
type DeviceLister interface {
	List(ctx context.Context) ([]devices.DeviceSummary, error)
}

// DeviceDeleter deletes a registered device.
type DeviceDeleter interface {
	Delete(ctx context.Context, id string) error
}

// RegistrationLimiter applies registration limits for remote IP addresses.
type RegistrationLimiter interface {
	Allow(remoteIP string) bool
}

// NewRouter builds the HTTP router for the PhotoVault API.
// spaHandler, when non-nil, is mounted as the catch-all fallback to serve
// the embedded React SPA for all non-API routes.
func NewRouter(logger *slog.Logger, deviceRegistrar DeviceRegistrar, registrationLimiter RegistrationLimiter, authenticator authn.Authenticator, uploadHandler, fileHandler http.Handler, syncHandler *SyncHandler, spaHandler http.Handler, mediaHandlers ...http.Handler) http.Handler {
	var metrics interface {
		Observe(string, string, int, time.Duration)
	}
	if len(mediaHandlers) > 2 {
		if ops, ok := mediaHandlers[2].(*OperationsHandler); ok {
			metrics = ops.metrics
		}
	}
	router := chi.NewRouter()
	router.Use(corsMiddleware)
	router.Use(middleware.RequestID)
	router.Use(middleware.RealIP)
	router.Use(authn.NetworkRestrictionMiddleware(logger))
	router.Use(requestLogger(logger, metrics))
	router.Use(middleware.Recoverer)
	// Compress API JSON responses and SPA assets (never media blobs — already compressed).
	router.Use(middleware.Compress(5, "application/json", "text/html", "text/css", "application/javascript"))
	if metrics == nil {
		router.Get("/health", health)
	}
	if adminProvider, ok := deviceRegistrar.(AdminProvider); ok {
		router.Get("/auth/bootstrap", AuthBootstrap(adminProvider))
	}
	router.Post("/devices/register", registerDevice(deviceRegistrar, registrationLimiter))
	auth := authn.Middleware(logger, authenticator)
	if lister, ok := deviceRegistrar.(DeviceLister); ok {
		router.With(auth).Get("/devices", listDevices(lister))
	}
	if deleter, ok := deviceRegistrar.(DeviceDeleter); ok {
		router.With(auth).Delete("/devices/{id}", deleteDevice(deleter))
	}
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
			router.With(auth).Post("/media/{id}/restore", index.Restore)
			router.With(auth).Delete("/media/{id}/permanent", index.PermanentDelete)
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
		if storageH, ok := mediaHandlers[3].(*StorageHandler); ok {
			router.With(auth).Post("/storage/scan", storageH.Scan)
			router.With(auth).Get("/storage/scan/status", storageH.ScanStatus)
			router.With(auth).Post("/storage/pick-folder", storageH.PickFolder)
			router.With(auth).Get("/storage/config", storageH.GetConfig)
			router.With(auth).Post("/storage/config", storageH.UpdateConfig)
		}
	}
	// SPA catch-all: serve the embedded React frontend for all non-API routes.
	// Only mounted in production (spaHandler is nil in dev mode).
	if spaHandler != nil {
		router.Handle("/*", spaHandler)
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

func listDevices(lister DeviceLister) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		devList, err := lister.List(request.Context())
		if err != nil {
			writeError(writer, http.StatusInternalServerError, "internal_error", "failed to list devices")
			return
		}
		writeJSON(writer, http.StatusOK, map[string]any{"devices": devList})
	}
}

func deleteDevice(deleter DeviceDeleter) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		id := chi.URLParam(request, "id")
		if id == "" {
			writeError(writer, http.StatusBadRequest, "invalid_request", "device id required")
			return
		}
		if err := deleter.Delete(request.Context(), id); err != nil {
			if errors.Is(err, devices.ErrCannotDeleteAdmin) {
				writeError(writer, http.StatusForbidden, "forbidden", "cannot delete server host admin device")
				return
			}
			writeError(writer, http.StatusInternalServerError, "internal_error", "failed to delete device")
			return
		}
		writeJSON(writer, http.StatusOK, map[string]string{"status": "deleted", "id": id})
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

func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Access-Control-Allow-Origin", "*")
		writer.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
		writer.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Range, Chunk-Nonce, Chunk-Tag, Chunk-Length")
		writer.Header().Set("Access-Control-Expose-Headers", "Content-Length, Content-Range, ETag")
		if request.Method == http.MethodOptions {
			writer.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(writer, request)
	})
}

package httpapi

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/go-chi/chi/v5"

	"photovault/internal/authn"
	"photovault/internal/files"
	"photovault/internal/httperror"
)

// OriginalMediaService opens originals without exposing storage paths to HTTP handlers.
type OriginalMediaService interface {
	OpenOriginal(ctx context.Context, id string) (files.File, *os.File, error)
}

type DerivedMediaService interface {
	OpenThumbnail(ctx context.Context, id string) (files.File, *os.File, error)
	OpenPreview(ctx context.Context, id string) (files.File, *os.File, error)
}

// MediaHandler serves authenticated original-media downloads.
type MediaHandler struct {
	service OriginalMediaService
	logger  *slog.Logger
	now     func() time.Time
}

// Thumbnail serves GET /media/{id}/thumbnail.
func (handler *MediaHandler) Thumbnail(writer http.ResponseWriter, request *http.Request) {
	handler.derived(writer, request, true)
}

// Preview serves GET /media/{id}/preview.
func (handler *MediaHandler) Preview(writer http.ResponseWriter, request *http.Request) {
	handler.derived(writer, request, false)
}

func (handler *MediaHandler) derived(writer http.ResponseWriter, request *http.Request, thumbnail bool) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	service, ok := handler.service.(DerivedMediaService)
	if !ok {
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
		return
	}
	id := chi.URLParam(request, "id")
	var file files.File
	var blob *os.File
	var err error
	if thumbnail {
		file, blob, err = service.OpenThumbnail(request.Context(), id)
	} else {
		file, blob, err = service.OpenPreview(request.Context(), id)
	}
	if errors.Is(err, files.ErrFileNotFound) || errors.Is(err, files.ErrDerivativeNotFound) {
		httperror.Write(writer, http.StatusNotFound, "media_not_ready", "media is not available")
		return
	}
	if err != nil {
		handler.logger.Error("derived media retrieval failed", "device_id", device.ID, "file_id", id, "error", err)
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
		return
	}
	defer blob.Close()
	name, contentType, tag := "preview.mp4", "video/mp4", file.Hash+"-preview"
	if thumbnail {
		name, contentType, tag = "thumbnail.jpg", "image/jpeg", file.Hash+"-thumbnail"
	}
	writer.Header().Set("Content-Type", contentType)
	writer.Header().Set("ETag", `"`+tag+`"`)
	// Thumbnails and previews are content-addressed (keyed by SHA-256 hash) and never change.
	// Allow browsers and CDNs to cache them indefinitely to eliminate redundant downloads.
	writer.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	http.ServeContent(writer, request, name, file.UploadedAt, blob)
}

// NewMediaHandler constructs a media handler backed by an original-media service.
func NewMediaHandler(service OriginalMediaService, logger *slog.Logger) *MediaHandler {
	return &MediaHandler{service: service, logger: logger, now: time.Now}
}

// ServeHTTP serves the original route when MediaHandler is registered directly.
func (handler *MediaHandler) ServeHTTP(writer http.ResponseWriter, request *http.Request) {
	handler.Original(writer, request)
}

// Original serves GET /media/{id}/original with conditional and byte-range support.
func (handler *MediaHandler) Original(writer http.ResponseWriter, request *http.Request) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	fileID := chi.URLParam(request, "id")
	startedAt := handler.now()
	file, blob, err := handler.service.OpenOriginal(request.Context(), fileID)
	if errors.Is(err, files.ErrFileNotFound) {
		httperror.Write(writer, http.StatusNotFound, "file_not_found", "file not found")
		return
	}
	if err != nil {
		handler.logger.Error("media retrieval failed", "device_id", device.ID, "file_id", fileID, "duration_ms", handler.now().Sub(startedAt).Milliseconds(), "error", err)
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
		return
	}
	defer blob.Close()

	writer.Header().Set("Content-Type", file.MIMEType)
	writer.Header().Set("ETag", `"`+file.Hash+`"`)
	if request.URL.Query().Get("download") == "1" || request.URL.Query().Get("attachment") == "1" {
		writer.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, filepath.Base(file.OriginalFilename)))
	}
	http.ServeContent(writer, request, file.OriginalFilename, file.UploadedAt, blob)
	handler.logger.Info("media retrieval completed", "device_id", device.ID, "file_id", file.ID, "size_bytes", file.SizeBytes, "duration_ms", handler.now().Sub(startedAt).Milliseconds())
}

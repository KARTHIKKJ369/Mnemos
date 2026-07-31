package httpapi

import (
	"context"
	"encoding/hex"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"photovault/internal/authn"
	"photovault/internal/files"
	"photovault/internal/httperror"
)

const sha256HexLength = 64

// HashExistenceService provides validated hash-existence lookups.
type HashExistenceService interface {
	Exists(ctx context.Context, hash string) (files.Existence, bool, error)
}

// FileHandler serves file metadata endpoints.
type FileHandler struct {
	service HashExistenceService
	logger  *slog.Logger
	now     func() time.Time
}

// NewFileHandler constructs file metadata handlers backed by a file service.
func NewFileHandler(service HashExistenceService, logger *slog.Logger) *FileHandler {
	return &FileHandler{service: service, logger: logger, now: time.Now}
}

// Exists serves GET /files/exists for an authenticated device.
func (handler *FileHandler) Exists(writer http.ResponseWriter, request *http.Request) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	hash, err := normalizeSHA256(request.URL.Query().Get("hash"))
	if err != nil {
		httperror.Write(writer, http.StatusBadRequest, "invalid_hash", "hash must be exactly 64 hexadecimal characters")
		return
	}
	startedAt := handler.now()
	existence, exists, err := handler.service.Exists(request.Context(), hash)
	if err != nil {
		handler.logger.Error("file existence lookup failed", "device_id", device.ID, "device_name", device.Name, "hash", hash, "duration_ms", handler.now().Sub(startedAt).Milliseconds(), "error", err)
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
		return
	}
	handler.logger.Info("file existence lookup", "device_id", device.ID, "device_name", device.Name, "hash", hash, "exists", exists, "duration_ms", handler.now().Sub(startedAt).Milliseconds())
	if !exists {
		writeJSON(writer, http.StatusOK, map[string]bool{"exists": false})
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{"exists": true, "file_id": existence.FileID, "size_bytes": existence.SizeBytes})
}

func normalizeSHA256(value string) (string, error) {
	hash := strings.ToLower(strings.TrimSpace(value))
	if len(hash) != sha256HexLength {
		return "", fmt.Errorf("invalid SHA-256 length")
	}
	if _, err := hex.DecodeString(hash); err != nil {
		return "", fmt.Errorf("decode SHA-256: %w", err)
	}
	return hash, nil
}

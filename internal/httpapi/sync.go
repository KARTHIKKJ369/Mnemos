package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"photovault/internal/authn"
	"photovault/internal/httperror"
	"photovault/internal/synchronization"
)

const maxSyncAckRequestBytes = 1 << 20

// SyncCoordinator provides validated sync-diff and sync-ack operations.
type SyncCoordinator interface {
	Diff(ctx context.Context, deviceID string, since *int64, requestedLimit *int) (synchronization.Diff, error)
	Ack(ctx context.Context, deviceID string, fileIDs []string) (int, error)
}

// SyncHandler serves synchronization endpoints.
type SyncHandler struct {
	service SyncCoordinator
	logger  *slog.Logger
	now     func() time.Time
}

// NewSyncHandler constructs sync handlers backed by a sync service.
func NewSyncHandler(service SyncCoordinator, logger *slog.Logger) *SyncHandler {
	return &SyncHandler{service: service, logger: logger, now: time.Now}
}

// Diff serves GET /sync/diff for the authenticated device.
func (handler *SyncHandler) Diff(writer http.ResponseWriter, request *http.Request) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	since, sinceErr := parseSince(request.URL.Query().Get("since"))
	if sinceErr != nil {
		httperror.Write(writer, http.StatusBadRequest, "invalid_since", "since must be a non-negative integer timestamp in milliseconds")
		return
	}
	limit, limitProvided, limitErr := parseLimit(request.URL.Query().Get("limit"))
	if limitErr != nil {
		httperror.Write(writer, http.StatusBadRequest, "invalid_limit", "limit must be a positive integer")
		return
	}
	var requestedLimit *int
	if limitProvided {
		requestedLimit = &limit
	}
	startedAt := handler.now()
	result, err := handler.service.Diff(request.Context(), device.ID, since, requestedLimit)
	if err != nil {
		handler.writeDiffError(writer, device.ID, device.Name, startedAt, err)
		return
	}
	handler.logger.Info("sync diff completed",
		"device_id", device.ID,
		"device_name", device.Name,
		"file_count", len(result.Files),
		"duration_ms", handler.now().Sub(startedAt).Milliseconds(),
	)
	writeJSON(writer, http.StatusOK, diffResponse(result))
}

// Ack serves POST /sync/ack for the authenticated device.
func (handler *SyncHandler) Ack(writer http.ResponseWriter, request *http.Request) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	request.Body = http.MaxBytesReader(writer, request.Body, maxSyncAckRequestBytes)
	defer request.Body.Close()

	var input struct {
		FileIDs []string `json:"file_ids"`
	}
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&input); err != nil {
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "request body must be valid JSON")
		return
	}
	if err := ensureSingleJSONValue(decoder); err != nil {
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "request body must contain one JSON object")
		return
	}
	startedAt := handler.now()
	acknowledged, err := handler.service.Ack(request.Context(), device.ID, input.FileIDs)
	if err != nil {
		handler.writeAckError(writer, device.ID, device.Name, startedAt, err)
		return
	}
	handler.logger.Info("sync ack completed",
		"device_id", device.ID,
		"device_name", device.Name,
		"acknowledged", acknowledged,
		"duration_ms", handler.now().Sub(startedAt).Milliseconds(),
	)
	writeJSON(writer, http.StatusOK, map[string]int{"acknowledged": acknowledged})
}

func (handler *SyncHandler) writeDiffError(writer http.ResponseWriter, deviceID, deviceName string, startedAt time.Time, err error) {
	handler.logger.Error("sync diff failed",
		"device_id", deviceID,
		"device_name", deviceName,
		"duration_ms", handler.now().Sub(startedAt).Milliseconds(),
		"error", err,
	)
	switch {
	case errors.Is(err, synchronization.ErrInvalidSince):
		httperror.Write(writer, http.StatusBadRequest, "invalid_since", "since must be a non-negative integer timestamp in milliseconds")
	case errors.Is(err, synchronization.ErrInvalidLimit):
		httperror.Write(writer, http.StatusBadRequest, "invalid_limit", "limit must be within the configured sync maximum")
	default:
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
	}
}

func (handler *SyncHandler) writeAckError(writer http.ResponseWriter, deviceID, deviceName string, startedAt time.Time, err error) {
	handler.logger.Error("sync ack failed",
		"device_id", deviceID,
		"device_name", deviceName,
		"duration_ms", handler.now().Sub(startedAt).Milliseconds(),
		"error", err,
	)
	switch {
	case errors.Is(err, synchronization.ErrEmptyBatch):
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "file_ids must contain at least one file ID")
	case errors.Is(err, synchronization.ErrDuplicateFileID):
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "file_ids must not contain duplicates")
	case errors.Is(err, synchronization.ErrInvalidFileID):
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "each file_id must be a valid UUID")
	case errors.Is(err, synchronization.ErrUnknownFileID):
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "one or more file_ids do not exist")
	case errors.Is(err, synchronization.ErrBatchTooLarge):
		httperror.Write(writer, http.StatusBadRequest, "invalid_request", "file_ids exceeds the configured maximum batch size")
	default:
		httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
	}
}

func parseSince(value string) (*int64, error) {
	value = trimQueryValue(value)
	if value == "" {
		return nil, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed < 0 {
		return nil, errors.New("invalid since")
	}
	return &parsed, nil
}

func parseLimit(value string) (int, bool, error) {
	value = trimQueryValue(value)
	if value == "" {
		return 0, false, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return 0, true, errors.New("invalid limit")
	}
	return parsed, true, nil
}

func trimQueryValue(value string) string {
	return strings.TrimSpace(value)
}

func diffResponse(result synchronization.Diff) map[string]any {
	files := make([]map[string]any, 0, len(result.Files))
	for _, file := range result.Files {
		files = append(files, map[string]any{
			"file_id":              file.FileID,
			"hash":                 file.Hash,
			"filename":             file.Filename,
			"mime_type":            file.MIMEType,
			"size_bytes":           file.SizeBytes,
			"thumbnail_available":  file.ThumbnailAvailable,
			"preview_available":    file.PreviewAvailable,
			"uploaded_at":          file.UploadedAt,
		})
	}
	response := map[string]any{"files": files}
	if result.NextSince != nil {
		response["next_since"] = *result.NextSince
	}
	return response
}

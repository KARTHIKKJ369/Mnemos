// Package uploads implements streamed media upload handling.
package uploads

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"photovault/internal/authn"
	"photovault/internal/files"
	"photovault/internal/httperror"
	"photovault/internal/storage"
)

const contentSniffBytes = 512

// Handler accepts authenticated multipart file uploads.
type Handler struct {
	blobStore     *storage.BlobStore
	fileStore     *files.Repository
	maximumSize   int64
	logger        *slog.Logger
	now           func() time.Time
	temporaryRoot string
	jobs          []JobEnqueuer
}

// JobEnqueuer persists asynchronous media processing work.
type JobEnqueuer interface {
	Enqueue(context.Context, string) error
}

// NewHandler constructs an upload handler with the configured maximum request size.
func NewHandler(blobStore *storage.BlobStore, fileStore *files.Repository, maximumSize int64, temporaryRoot string, logger *slog.Logger, jobEnqueuers ...JobEnqueuer) *Handler {
	return &Handler{blobStore: blobStore, fileStore: fileStore, maximumSize: maximumSize, temporaryRoot: temporaryRoot, logger: logger, now: time.Now, jobs: jobEnqueuers}
}

// ServeHTTP receives one multipart field named file and stores it content-addressably.
func (handler *Handler) ServeHTTP(writer http.ResponseWriter, request *http.Request) {
	device, ok := authn.DeviceFromContext(request.Context())
	if !ok {
		httperror.Write(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	startedAt := handler.now()
	handler.logger.Info("upload started", "device_id", device.ID, "device_name", device.Name)

	request.Body = http.MaxBytesReader(writer, request.Body, handler.maximumSize)
	part, err := uploadPart(request)
	if err != nil {
		handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, 0, false, err)
		return
	}
	defer part.Close()

	temporaryFile, err := os.CreateTemp(handler.temporaryRoot, ".upload-*")
	if err != nil {
		handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, 0, false, fmt.Errorf("create temporary file: %w", err))
		return
	}
	temporaryPath := temporaryFile.Name()
	defer os.Remove(temporaryPath)

	hasher := sha256.New()
	sniffer := &prefixWriter{limit: contentSniffBytes}
	bytesReceived, copyErr := io.Copy(io.MultiWriter(temporaryFile, hasher, sniffer), part)
	closeErr := temporaryFile.Close()
	if copyErr != nil {
		handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, bytesReceived, false, copyErr)
		return
	}
	if closeErr != nil {
		handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, bytesReceived, false, fmt.Errorf("close temporary file: %w", closeErr))
		return
	}

	hash := hex.EncodeToString(hasher.Sum(nil))
	mimeType := http.DetectContentType(sniffer.bytes)
	uploadedAt := handler.now()
	input := files.CreateInput{Hash: hash, OriginalFilename: originalFilename(part.FileName()), MIMEType: mimeType, SizeBytes: bytesReceived, UploadedByDeviceID: device.ID, UploadedAt: uploadedAt}

	var file files.File
	var deduplicated bool
	err = handler.blobStore.WithHashLock(hash, func() error {
		storagePath, blobExists, err := handler.blobStore.Finalize(temporaryPath, hash, mimeType, device, uploadedAt)
		if err != nil {
			return err
		}
		input.StoragePath = storagePath
		createdFile, metadataCreated, createErr := handler.fileStore.CreateOrGet(request.Context(), input)
		if createErr != nil {
			if !blobExists {
				if removeErr := handler.blobStore.Remove(storagePath); removeErr != nil {
					return fmt.Errorf("create metadata: %w; remove finalized blob: %v", createErr, removeErr)
				}
			}
			return createErr
		}
		file = createdFile
		deduplicated = blobExists || !metadataCreated
		return nil
	})
	if err != nil {
		handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, bytesReceived, false, err)
		return
	}
	for _, jobs := range handler.jobs {
		if err := jobs.Enqueue(request.Context(), file.ID); err != nil {
			handler.writeUploadError(writer, request, device.ID, device.Name, startedAt, bytesReceived, deduplicated, fmt.Errorf("enqueue media processing: %w", err))
			return
		}
	}

	handler.logger.Info("upload finished", "device_id", device.ID, "device_name", device.Name, "remote_ip", requestRemoteIP(request), "user_agent", request.UserAgent(), "duration_ms", handler.now().Sub(startedAt).Milliseconds(), "bytes_received", bytesReceived, "deduplicated", deduplicated)
	writeJSON(writer, http.StatusCreated, map[string]any{"file_id": file.ID, "hash": file.Hash, "size_bytes": file.SizeBytes, "mime_type": file.MIMEType, "status": file.Status, "deduplicated": deduplicated})
}

func (handler *Handler) writeUploadError(writer http.ResponseWriter, request *http.Request, deviceID, deviceName string, startedAt time.Time, bytesReceived int64, deduplicated bool, err error) {
	handler.logger.Warn("upload failed", "device_id", deviceID, "device_name", deviceName, "remote_ip", requestRemoteIP(request), "user_agent", request.UserAgent(), "duration_ms", handler.now().Sub(startedAt).Milliseconds(), "bytes_received", bytesReceived, "deduplicated", deduplicated, "error", err)
	if isRequestTooLarge(err) {
		httperror.Write(writer, http.StatusRequestEntityTooLarge, "upload_too_large", "upload exceeds the configured size limit")
		return
	}
	if errors.Is(err, errInvalidMultipart) {
		httperror.Write(writer, http.StatusBadRequest, "invalid_multipart", "request must contain one multipart file field named file")
		return
	}
	httperror.Write(writer, http.StatusInternalServerError, "internal_error", "an internal error occurred")
}

var errInvalidMultipart = errors.New("invalid multipart upload")

func uploadPart(request *http.Request) (*multipart.Part, error) {
	reader, err := request.MultipartReader()
	if err != nil {
		return nil, errInvalidMultipart
	}
	for {
		part, err := reader.NextPart()
		if errors.Is(err, io.EOF) {
			return nil, errInvalidMultipart
		}
		if err != nil {
			return nil, err
		}
		if part.FormName() == "file" && part.FileName() != "" {
			return part, nil
		}
		part.Close()
	}
}

func originalFilename(value string) string {
	name := filepath.Base(strings.TrimSpace(value))
	if name == "." || name == string(filepath.Separator) || name == "" {
		return "upload"
	}
	return name
}

func isRequestTooLarge(err error) bool {
	var maxBytesError *http.MaxBytesError
	return errors.As(err, &maxBytesError)
}

type prefixWriter struct {
	bytes []byte
	limit int
}

func (writer *prefixWriter) Write(value []byte) (int, error) {
	originalLength := len(value)
	remaining := writer.limit - len(writer.bytes)
	if remaining > 0 {
		if len(value) > remaining {
			value = value[:remaining]
		}
		writer.bytes = append(writer.bytes, value...)
	}
	return originalLength, nil
}

func requestRemoteIP(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err == nil {
		return host
	}
	return request.RemoteAddr
}

func writeJSON(writer http.ResponseWriter, status int, response any) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(response)
}

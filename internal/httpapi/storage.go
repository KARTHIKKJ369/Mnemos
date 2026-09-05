package httpapi

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"photovault/internal/authn"
	"photovault/internal/config"
	"photovault/internal/devices"
	"photovault/internal/scanning"
)

// AdminProvider provides the current admin device registration.
type AdminProvider interface {
	AdminRegistration() (devices.Registration, bool)
}

// scanStatus tracks the current or last completed scan.
type scanStatus struct {
	mu          sync.Mutex
	running     bool
	lastPath    string
	lastStarted time.Time
	lastResult  *scanning.ScanResult
	lastError   string
}

func (s *scanStatus) start(path string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.running {
		return false
	}
	s.running = true
	s.lastPath = path
	s.lastStarted = time.Now()
	s.lastResult = nil
	s.lastError = ""
	return true
}

func (s *scanStatus) finish(res scanning.ScanResult, err error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.running = false
	s.lastResult = &res
	if err != nil {
		s.lastError = err.Error()
	}
}

func (s *scanStatus) snapshot() map[string]any {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := map[string]any{
		"running":   s.running,
		"last_path": s.lastPath,
	}
	if !s.lastStarted.IsZero() {
		out["last_started"] = s.lastStarted.UTC().Format(time.RFC3339)
	}
	if s.lastResult != nil {
		out["last_result"] = s.lastResult
	}
	if s.lastError != "" {
		out["last_error"] = s.lastError
	}
	return out
}

// StorageHandler handles storage operations and directory scanning.
type StorageHandler struct {
	scanner       *scanning.FolderScanner
	adminProvider AdminProvider
	logger        *slog.Logger
	storagePath   string
	envPath       string
	status        scanStatus
	// serverCtx is the application-level context: scan jobs outlive HTTP requests.
	serverCtx context.Context
}

func (handler *StorageHandler) targetEnvPath() string {
	if handler.envPath != "" {
		return handler.envPath
	}
	return config.DefaultDotEnvPath
}

// SetEnvPath sets an explicit .env path (useful for tests or custom deployments).
func (handler *StorageHandler) SetEnvPath(path string) {
	handler.envPath = path
}

// NewStorageHandler constructs a storage handler.
func NewStorageHandler(scanner *scanning.FolderScanner, adminProvider AdminProvider, logger *slog.Logger, storagePath string, serverCtx ...context.Context) *StorageHandler {
	ctx := context.Background()
	if len(serverCtx) > 0 && serverCtx[0] != nil {
		ctx = serverCtx[0]
	}
	return &StorageHandler{
		scanner:       scanner,
		adminProvider: adminProvider,
		logger:        logger,
		storagePath:   storagePath,
		serverCtx:     ctx,
	}
}

// Scan processes a request to scan a folder on disk and ingest media files.
// The scan runs against the application-level context, so browser refreshes
// do not abort the operation.
func (handler *StorageHandler) Scan(writer http.ResponseWriter, request *http.Request) {
	if _, ok := authn.DeviceFromContext(request.Context()); !ok {
		writeError(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	admin, ok := handler.adminProvider.AdminRegistration()
	if !ok {
		writeError(writer, http.StatusInternalServerError, "admin_not_configured", "server admin device is not initialized")
		return
	}

	var input struct {
		Path string `json:"path"`
	}
	if request.Body != nil && request.ContentLength > 0 {
		_ = json.NewDecoder(request.Body).Decode(&input)
	}

	// Reject concurrent scans; return the current status instead.
	if !handler.status.start(input.Path) {
		writeJSON(writer, http.StatusConflict, map[string]any{
			"error":  "scan_in_progress",
			"status": handler.status.snapshot(),
		})
		return
	}

	// Run in background against the server context so browser navigation never kills it.
	go func() {
		result, err := handler.scanner.Scan(handler.serverCtx, input.Path, admin.Device)
		handler.status.finish(result, err)
		if err != nil {
			handler.logger.Error("folder scan failed", "path", input.Path, "err", err)
		} else {
			handler.logger.Info("folder scan finished", "path", input.Path,
				"imported", result.Imported, "already_indexed", result.AlreadyIndexed,
				"errors", result.Errors)
		}
	}()

	// Respond immediately with accepted status so the client can poll.
	writeJSON(writer, http.StatusAccepted, map[string]any{
		"message": fmt.Sprintf("scan started for path: %q – poll /storage/scan/status for progress", input.Path),
		"status":  handler.status.snapshot(),
	})
}

// ScanStatus returns the current or last-completed scan status.
func (handler *StorageHandler) ScanStatus(writer http.ResponseWriter, request *http.Request) {
	if _, ok := authn.DeviceFromContext(request.Context()); !ok {
		writeError(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}
	writeJSON(writer, http.StatusOK, handler.status.snapshot())
}

// PickFolder launches the native system folder picker (macOS Finder) to select a folder on disk.
func (handler *StorageHandler) PickFolder(writer http.ResponseWriter, request *http.Request) {
	path, cancelled, err := pickFolderDialog(request.Context())
	if err != nil {
		handler.logger.Error("system folder picker failed", "err", err)
		writeError(writer, http.StatusInternalServerError, "picker_error", "Failed to open system folder picker")
		return
	}
	if cancelled {
		writeJSON(writer, http.StatusOK, map[string]any{
			"cancelled": true,
		})
		return
	}

	writeJSON(writer, http.StatusOK, map[string]any{
		"cancelled": false,
		"path":      path,
	})
}

// GetConfig returns the current storage path, .env file path, and whether .env exists.
func (handler *StorageHandler) GetConfig(writer http.ResponseWriter, request *http.Request) {
	if _, ok := authn.DeviceFromContext(request.Context()); !ok {
		writeError(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}

	envPath := handler.targetEnvPath()
	absEnv, _ := filepath.Abs(envPath)
	_, err := os.Stat(envPath)
	envExists := err == nil

	absStorage, _ := filepath.Abs(handler.storagePath)

	writeJSON(writer, http.StatusOK, map[string]any{
		"storage_path": absStorage,
		"env_path":     absEnv,
		"env_exists":   envExists,
	})
}

// UpdateConfig writes PHOTOVAULT_STORAGE_PATH to .env after validating the requested path.
func (handler *StorageHandler) UpdateConfig(writer http.ResponseWriter, request *http.Request) {
	if _, ok := authn.DeviceFromContext(request.Context()); !ok {
		writeError(writer, http.StatusUnauthorized, "unauthorized", "authentication failed")
		return
	}

	var input struct {
		StoragePath string `json:"storage_path"`
	}
	if err := json.NewDecoder(request.Body).Decode(&input); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_json", "malformed request payload")
		return
	}

	path := strings.TrimSpace(input.StoragePath)
	if path == "" {
		writeError(writer, http.StatusBadRequest, "invalid_path", "storage_path cannot be empty")
		return
	}

	cleanPath := filepath.Clean(path)
	if !filepath.IsAbs(cleanPath) {
		abs, err := filepath.Abs(cleanPath)
		if err == nil {
			cleanPath = abs
		}
	}

	info, err := os.Stat(cleanPath)
	if err == nil {
		if !info.IsDir() {
			writeError(writer, http.StatusBadRequest, "invalid_path", "specified storage path is a file, not a directory")
			return
		}
	} else if os.IsNotExist(err) {
		parent := filepath.Dir(cleanPath)
		parentInfo, parentErr := os.Stat(parent)
		if parentErr != nil || !parentInfo.IsDir() {
			writeError(writer, http.StatusBadRequest, "parent_not_found", "parent directory of the storage path does not exist")
			return
		}
	}

	if err := config.UpdateDotEnv(handler.targetEnvPath(), "PHOTOVAULT_STORAGE_PATH", cleanPath); err != nil {
		handler.logger.Error("failed to update .env", "err", err)
		writeError(writer, http.StatusInternalServerError, "env_write_error", "failed to update .env file")
		return
	}

	writeJSON(writer, http.StatusOK, map[string]any{
		"status":           "saved",
		"storage_path":     cleanPath,
		"requires_restart": true,
		"message":          "Vault storage path saved to .env. Restart PhotoVault to load the new directory.",
	})
}

// ServeHTTP implements http.Handler for StorageHandler.
func (handler *StorageHandler) ServeHTTP(writer http.ResponseWriter, request *http.Request) {
	handler.Scan(writer, request)
}

func pickFolderDialog(ctx context.Context) (string, bool, error) {
	if runtime.GOOS == "darwin" {
		script := `POSIX path of (choose folder with prompt "Select photo folder for PhotoVault:")`
		cmd := exec.CommandContext(ctx, "osascript", "-e", script)
		output, err := cmd.CombinedOutput()
		if err != nil {
			outStr := string(output)
			if strings.Contains(outStr, "User canceled") || strings.Contains(outStr, "-128") {
				return "", true, nil
			}
			return "", false, fmt.Errorf("osascript error: %w: %s", err, outStr)
		}
		path := strings.TrimSpace(string(output))
		// osascript returns paths with trailing slash; normalise.
		path = strings.TrimSuffix(path, "/")
		return path, false, nil
	}
	return "", false, fmt.Errorf("native folder picker is only supported on macOS")
}

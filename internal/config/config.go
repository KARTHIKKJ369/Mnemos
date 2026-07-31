// Package config loads and validates PhotoVault server configuration.
package config

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const (
	defaultHTTPAddress      = "127.0.0.1:8080"
	defaultStoragePath      = "storage"
	defaultShutdownTimeout  = 15 * time.Second
	defaultMaxUploadBytes   = int64(5 * 1024 * 1024 * 1024)
	defaultHashCacheSize    = 1024
	defaultHashCacheTTL     = 5 * time.Minute
	defaultSyncDefaultLimit = 100
	defaultSyncMaxLimit     = 1000
	defaultSyncAckMaxBatch  = 500
)

// Config contains all runtime configuration for the server.
type Config struct {
	HTTPAddress     string
	StoragePath     string
	DatabasePath    string
	LogLevel        slog.Level
	ShutdownTimeout time.Duration
	MaxUploadBytes  int64
	HashCacheSize     int
	HashCacheTTL      time.Duration
	SyncDefaultLimit  int
	SyncMaxLimit      int
	SyncAckMaxBatch   int
}

// Load reads configuration from environment variables and validates it.
func Load() (Config, error) {
	storagePath := valueOrDefault("PHOTOVAULT_STORAGE_PATH", defaultStoragePath)
	storagePath = filepath.Clean(storagePath)

	logLevel, err := parseLogLevel(valueOrDefault("PHOTOVAULT_LOG_LEVEL", "info"))
	if err != nil {
		return Config{}, err
	}

	shutdownTimeout, err := time.ParseDuration(valueOrDefault("PHOTOVAULT_SHUTDOWN_TIMEOUT", defaultShutdownTimeout.String()))
	if err != nil {
		return Config{}, fmt.Errorf("parse PHOTOVAULT_SHUTDOWN_TIMEOUT: %w", err)
	}
	if shutdownTimeout <= 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_SHUTDOWN_TIMEOUT must be positive")
	}
	maxUploadBytes, err := strconv.ParseInt(valueOrDefault("PHOTOVAULT_MAX_UPLOAD_BYTES", strconv.FormatInt(defaultMaxUploadBytes, 10)), 10, 64)
	if err != nil || maxUploadBytes <= 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_MAX_UPLOAD_BYTES must be a positive integer")
	}
	hashCacheSize, err := strconv.Atoi(valueOrDefault("PHOTOVAULT_HASH_CACHE_SIZE", strconv.Itoa(defaultHashCacheSize)))
	if err != nil || hashCacheSize < 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_HASH_CACHE_SIZE must be a non-negative integer")
	}
	hashCacheTTL, err := time.ParseDuration(valueOrDefault("PHOTOVAULT_HASH_CACHE_TTL", defaultHashCacheTTL.String()))
	if err != nil || hashCacheTTL < 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_HASH_CACHE_TTL must be a non-negative duration")
	}
	syncDefaultLimit, err := strconv.Atoi(valueOrDefault("PHOTOVAULT_SYNC_DEFAULT_LIMIT", strconv.Itoa(defaultSyncDefaultLimit)))
	if err != nil || syncDefaultLimit <= 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_SYNC_DEFAULT_LIMIT must be a positive integer")
	}
	syncMaxLimit, err := strconv.Atoi(valueOrDefault("PHOTOVAULT_SYNC_MAX_LIMIT", strconv.Itoa(defaultSyncMaxLimit)))
	if err != nil || syncMaxLimit <= 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_SYNC_MAX_LIMIT must be a positive integer")
	}
	if syncDefaultLimit > syncMaxLimit {
		return Config{}, fmt.Errorf("PHOTOVAULT_SYNC_DEFAULT_LIMIT must not exceed PHOTOVAULT_SYNC_MAX_LIMIT")
	}
	syncAckMaxBatch, err := strconv.Atoi(valueOrDefault("PHOTOVAULT_SYNC_ACK_MAX_BATCH", strconv.Itoa(defaultSyncAckMaxBatch)))
	if err != nil || syncAckMaxBatch <= 0 {
		return Config{}, fmt.Errorf("PHOTOVAULT_SYNC_ACK_MAX_BATCH must be a positive integer")
	}

	return Config{
		HTTPAddress:      valueOrDefault("PHOTOVAULT_HTTP_ADDRESS", defaultHTTPAddress),
		StoragePath:      storagePath,
		DatabasePath:     filepath.Join(storagePath, "vault.db"),
		LogLevel:         logLevel,
		ShutdownTimeout:  shutdownTimeout,
		MaxUploadBytes:   maxUploadBytes,
		HashCacheSize:    hashCacheSize,
		HashCacheTTL:     hashCacheTTL,
		SyncDefaultLimit: syncDefaultLimit,
		SyncMaxLimit:     syncMaxLimit,
		SyncAckMaxBatch:  syncAckMaxBatch,
	}, nil
}

func valueOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func parseLogLevel(value string) (slog.Level, error) {
	var level slog.Level
	if err := level.UnmarshalText([]byte(strings.ToUpper(value))); err != nil {
		return 0, fmt.Errorf("parse PHOTOVAULT_LOG_LEVEL: %w", err)
	}
	return level, nil
}

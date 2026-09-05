// Package config loads and validates PhotoVault server configuration.
package config

import (
	"bufio"
	"bytes"
	"errors"
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

	DefaultDotEnvPath = ".env"
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

// Load reads configuration from .env and environment variables, validating it.
func Load() (Config, error) {
	_ = ApplyDotEnv(DefaultDotEnvPath)

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

// LoadDotEnv reads key-value pairs from a .env file.
// If the file does not exist, it returns an empty map and nil error.
func LoadDotEnv(filename string) (map[string]string, error) {
	file, err := os.Open(filename)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return map[string]string{}, nil
		}
		return nil, fmt.Errorf("open .env: %w", err)
	}
	defer file.Close()

	result := make(map[string]string)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, val, ok := parseEnvLine(line)
		if ok {
			result[key] = val
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scan .env: %w", err)
	}
	return result, nil
}

// ApplyDotEnv loads .env and sets environment variables that are not already set.
func ApplyDotEnv(filename string) error {
	vars, err := LoadDotEnv(filename)
	if err != nil {
		return err
	}
	for k, v := range vars {
		if _, exists := os.LookupEnv(k); !exists {
			_ = os.Setenv(k, v)
		}
	}
	return nil
}

func parseEnvLine(line string) (string, string, bool) {
	line = strings.TrimPrefix(line, "export ")
	line = strings.TrimSpace(line)
	idx := strings.Index(line, "=")
	if idx <= 0 {
		return "", "", false
	}
	key := strings.TrimSpace(line[:idx])
	val := strings.TrimSpace(line[idx+1:])

	// Handle quotes
	if len(val) >= 2 {
		if (strings.HasPrefix(val, `"`) && strings.HasSuffix(val, `"`)) ||
			(strings.HasPrefix(val, `'`) && strings.HasSuffix(val, `'`)) {
			val = val[1 : len(val)-1]
		}
	}
	return key, val, true
}

// UpdateDotEnv updates or adds key=value in the specified .env file while preserving
// existing lines and comments. Writes atomically via a temporary file.
func UpdateDotEnv(filename, key, value string) error {
	dir := filepath.Dir(filename)
	if dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return fmt.Errorf("mkdir .env dir: %w", err)
		}
	}

	var lines []string
	keyFound := false
	prefixMatch := key + "="
	exportPrefixMatch := "export " + key + "="

	if data, err := os.ReadFile(filename); err == nil {
		scanner := bufio.NewScanner(bytes.NewReader(data))
		for scanner.Scan() {
			text := scanner.Text()
			trimmed := strings.TrimSpace(text)
			if strings.HasPrefix(trimmed, prefixMatch) || strings.HasPrefix(trimmed, exportPrefixMatch) {
				lines = append(lines, formatEnvLine(key, value))
				keyFound = true
			} else {
				lines = append(lines, text)
			}
		}
		if err := scanner.Err(); err != nil {
			return fmt.Errorf("read .env lines: %w", err)
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("read .env: %w", err)
	}

	if !keyFound {
		if len(lines) == 0 {
			lines = append(lines, "# PhotoVault Environment Configuration")
		}
		lines = append(lines, formatEnvLine(key, value))
	}

	tmpFile, err := os.CreateTemp(dir, ".env.tmp.*")
	if err != nil {
		return fmt.Errorf("create temp .env: %w", err)
	}
	tmpName := tmpFile.Name()
	defer func() {
		_ = tmpFile.Close()
		_ = os.Remove(tmpName)
	}()

	writer := bufio.NewWriter(tmpFile)
	for _, l := range lines {
		if _, err := writer.WriteString(l + "\n"); err != nil {
			return fmt.Errorf("write temp .env: %w", err)
		}
	}
	if err := writer.Flush(); err != nil {
		return fmt.Errorf("flush temp .env: %w", err)
	}
	if err := tmpFile.Sync(); err != nil {
		return fmt.Errorf("sync temp .env: %w", err)
	}
	if err := tmpFile.Close(); err != nil {
		return fmt.Errorf("close temp .env: %w", err)
	}

	if err := os.Rename(tmpName, filename); err != nil {
		return fmt.Errorf("atomic rename .env: %w", err)
	}
	return nil
}

func formatEnvLine(key, value string) string {
	if strings.ContainsAny(value, " \t\n\"'#$") {
		escaped := strings.ReplaceAll(value, `"`, `\"`)
		return fmt.Sprintf(`%s="%s"`, key, escaped)
	}
	return fmt.Sprintf("%s=%s", key, value)
}


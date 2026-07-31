package observability

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"syscall"
	"time"
)

type Health struct {
	Database                     *sql.DB
	StoragePath, Version, Commit string
	StartedAt                    time.Time
	Workers                      func() bool
}

func (h Health) Check(ctx context.Context) (map[string]any, error) {
	result := map[string]any{"version": h.Version, "build_commit": h.Commit, "uptime_seconds": int64(time.Since(h.StartedAt).Seconds()), "database": "ok", "blob_storage": "ok", "workers": "ok"}
	if err := h.Database.PingContext(ctx); err != nil {
		result["database"] = "error"
		return result, fmt.Errorf("database: %w", err)
	}
	var stat syscall.Statfs_t
	if err := syscall.Statfs(h.StoragePath, &stat); err != nil {
		result["blob_storage"] = "error"
		return result, fmt.Errorf("storage: %w", err)
	}
	result["disk_free_bytes"] = uint64(stat.Bavail) * uint64(stat.Bsize)
	if h.Workers != nil && !h.Workers() {
		result["workers"] = "error"
		return result, fmt.Errorf("workers unavailable")
	}
	return result, nil
}
func (h Health) Ready(ctx context.Context) error { _, err := h.Check(ctx); return err }
func EnsureStorage(path string) error            { _, err := os.Stat(path); return err }

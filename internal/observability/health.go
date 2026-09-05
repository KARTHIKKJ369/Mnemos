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
	result := map[string]any{
		"version":        h.Version,
		"build_commit":   h.Commit,
		"uptime_seconds": int64(time.Since(h.StartedAt).Seconds()),
		"database":       "ok",
		"blob_storage":   "ok",
		"workers":        "ok",
		"storage_path":   h.StoragePath,
	}
	if err := h.Database.PingContext(ctx); err != nil {
		result["database"] = "error"
		return result, fmt.Errorf("database: %w", err)
	}

	var totalMedia int64
	var vaultBytes int64
	_ = h.Database.QueryRowContext(ctx, "SELECT count(*), coalesce(sum(size_bytes), 0) FROM media_index WHERE deleted = 0").Scan(&totalMedia, &vaultBytes)
	result["total_media"] = totalMedia
	result["vault_bytes"] = vaultBytes

	var totalPhotos int64
	_ = h.Database.QueryRowContext(ctx, "SELECT count(*) FROM media_index WHERE deleted = 0 AND mime_type LIKE 'image/%'").Scan(&totalPhotos)
	result["total_photos"] = totalPhotos

	var totalVideos int64
	_ = h.Database.QueryRowContext(ctx, "SELECT count(*) FROM media_index WHERE deleted = 0 AND mime_type LIKE 'video/%'").Scan(&totalVideos)
	result["total_videos"] = totalVideos

	var totalDevices int64
	_ = h.Database.QueryRowContext(ctx, "SELECT count(*) FROM devices").Scan(&totalDevices)
	result["total_devices"] = totalDevices

	var stat syscall.Statfs_t
	if err := syscall.Statfs(h.StoragePath, &stat); err != nil {
		result["blob_storage"] = "error"
		return result, fmt.Errorf("storage: %w", err)
	}
	result["disk_free_bytes"] = uint64(stat.Bavail) * uint64(stat.Bsize)
	result["disk_total_bytes"] = uint64(stat.Blocks) * uint64(stat.Bsize)

	if h.Workers != nil && !h.Workers() {
		result["workers"] = "error"
		return result, fmt.Errorf("workers unavailable")
	}
	return result, nil
}

func (h Health) Ready(ctx context.Context) error { _, err := h.Check(ctx); return err }
func EnsureStorage(path string) error            { _, err := os.Stat(path); return err }

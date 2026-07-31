package devices

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

// SQLiteStore persists devices in the PhotoVault SQLite database.
type SQLiteStore struct {
	database *sql.DB
}

// NewSQLiteStore constructs a SQLite-backed device store.
func NewSQLiteStore(database *sql.DB) *SQLiteStore {
	return &SQLiteStore{database: database}
}

// Create persists a newly registered device and its token hash.
func (store *SQLiteStore) Create(ctx context.Context, device Device, tokenHash string, createdAt time.Time) error {
	createdAtMillis := createdAt.UnixMilli()
	_, err := store.database.ExecContext(ctx, `
		INSERT INTO devices (id, name, device_type, auth_token_hash, created_at, last_seen_at)
		VALUES (?, ?, ?, ?, ?, ?)
	`, device.ID, device.Name, device.DeviceType, tokenHash, createdAtMillis, createdAtMillis)
	if err != nil {
		return fmt.Errorf("insert device: %w", err)
	}
	return nil
}

// FindByTokenHash returns the device matching a persisted token hash.
func (store *SQLiteStore) FindByTokenHash(ctx context.Context, tokenHash string) (Device, string, error) {
	var device Device
	var storedHash string
	err := store.database.QueryRowContext(ctx, `
		SELECT id, name, device_type, auth_token_hash
		FROM devices
		WHERE auth_token_hash = ?
	`, tokenHash).Scan(&device.ID, &device.Name, &device.DeviceType, &storedHash)
	if errors.Is(err, sql.ErrNoRows) {
		return Device{}, invalidTokenHash, nil
	}
	if err != nil {
		return Device{}, "", fmt.Errorf("select device: %w", err)
	}
	return device, storedHash, nil
}

// UpdateLastSeen records the most recent authenticated request for a device.
func (store *SQLiteStore) UpdateLastSeen(ctx context.Context, deviceID string, seenAt time.Time) error {
	result, err := store.database.ExecContext(ctx, "UPDATE devices SET last_seen_at = ? WHERE id = ?", seenAt.UnixMilli(), deviceID)
	if err != nil {
		return fmt.Errorf("update device: %w", err)
	}
	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("read updated device count: %w", err)
	}
	if rowsAffected != 1 {
		return ErrInvalidToken
	}
	return nil
}

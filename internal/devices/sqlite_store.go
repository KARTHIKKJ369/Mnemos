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

// List returns all registered devices ordered by last seen timestamp descending.
func (store *SQLiteStore) List(ctx context.Context) ([]DeviceSummary, error) {
	rows, err := store.database.QueryContext(ctx, `
		SELECT id, name, device_type, created_at, last_seen_at
		FROM devices
		ORDER BY last_seen_at DESC
	`)
	if err != nil {
		return nil, fmt.Errorf("select devices: %w", err)
	}
	defer rows.Close()

	var result []DeviceSummary
	for rows.Next() {
		var d DeviceSummary
		var createdAtMillis, lastSeenAtMillis int64
		if err := rows.Scan(&d.ID, &d.Name, &d.DeviceType, &createdAtMillis, &lastSeenAtMillis); err != nil {
			return nil, fmt.Errorf("scan device: %w", err)
		}
		d.CreatedAt = time.UnixMilli(createdAtMillis).UTC()
		d.LastSeenAt = time.UnixMilli(lastSeenAtMillis).UTC()
		result = append(result, d)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate devices: %w", err)
	}
	if result == nil {
		result = []DeviceSummary{}
	}
	return result, nil
}

// Delete removes a registered device by its ID, reassigning its uploaded files to the admin device.
func (store *SQLiteStore) Delete(ctx context.Context, id string) error {
	var adminID string
	_ = store.database.QueryRowContext(ctx, "SELECT id FROM devices ORDER BY created_at ASC LIMIT 1").Scan(&adminID)
	if adminID != "" && adminID != id {
		_, _ = store.database.ExecContext(ctx, "UPDATE files SET uploaded_by_device_id = ? WHERE uploaded_by_device_id = ?", adminID, id)
	}
	_, err := store.database.ExecContext(ctx, `DELETE FROM devices WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("delete device: %w", err)
	}
	return nil
}


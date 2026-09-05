// Package devices manages registered PhotoVault devices and their credentials.
package devices

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/google/uuid"
)

const bearerTokenBytes = 32

const invalidTokenHash = "0000000000000000000000000000000000000000000000000000000000000000"

var (
	// ErrInvalidDeviceType indicates a device type outside the supported API values.
	ErrInvalidDeviceType = errors.New("invalid device type")
	// ErrInvalidDeviceName indicates an empty device name.
	ErrInvalidDeviceName = errors.New("invalid device name")
	// ErrInvalidToken indicates a missing, malformed, or unknown device token.
	ErrInvalidToken = errors.New("invalid device token")
)

// Device is a registered client device.
type Device struct {
	ID         string
	Name       string
	DeviceType string
}

// Registration contains a device identity and its one-time returned bearer token.
type Registration struct {
	Device Device
	Token  string
}

// DeviceSummary represents a registered device without its secret token hash.
type DeviceSummary struct {
	ID         string    `json:"id"`
	Name       string    `json:"name"`
	DeviceType string    `json:"device_type"`
	CreatedAt  time.Time `json:"created_at"`
	LastSeenAt time.Time `json:"last_seen_at"`
}

// Store persists device credentials and activity data.
type Store interface {
	Create(ctx context.Context, device Device, tokenHash string, createdAt time.Time) error
	FindByTokenHash(ctx context.Context, tokenHash string) (Device, string, error)
	UpdateLastSeen(ctx context.Context, deviceID string, seenAt time.Time) error
	List(ctx context.Context) ([]DeviceSummary, error)
	Delete(ctx context.Context, id string) error
}

// ErrCannotDeleteAdmin indicates an attempt to delete the server host admin device.
var ErrCannotDeleteAdmin = errors.New("cannot delete server host admin device")

// Service registers and authenticates PhotoVault devices.
type Service struct {
	store Store
	now   func() time.Time
	admin Registration
}

// NewService constructs a device service using the supplied persistent store.
func NewService(store Store) *Service {
	return &Service{store: store, now: time.Now}
}

// EnsureAdminDevice ensures a primary Admin device exists for the server host and returns its credentials.
func (service *Service) EnsureAdminDevice(ctx context.Context, adminTokenPath string) (Registration, error) {
	if adminTokenPath != "" {
		if data, err := os.ReadFile(adminTokenPath); err == nil {
			token := strings.TrimSpace(string(data))
			if token != "" {
				device, authErr := service.Authenticate(ctx, token)
				if authErr == nil {
					service.admin = Registration{Device: device, Token: token}
					return service.admin, nil
				}
			}
		}
	}

	reg, err := service.Register(ctx, "Server Host (Admin)", "mac")
	if err != nil {
		return Registration{}, fmt.Errorf("register admin device: %w", err)
	}
	service.admin = reg

	if adminTokenPath != "" {
		_ = os.WriteFile(adminTokenPath, []byte(reg.Token+"\n"), 0o600)
	}
	return reg, nil
}

// AdminRegistration returns the active admin registration if initialized.
func (service *Service) AdminRegistration() (Registration, bool) {
	if service.admin.Token != "" {
		return service.admin, true
	}
	return Registration{}, false
}

// Register validates and persists a new device, returning its only copy of the bearer token.
func (service *Service) Register(ctx context.Context, name, deviceType string) (Registration, error) {
	name = strings.TrimSpace(name)
	if len([]rune(name)) < 1 || len([]rune(name)) > 100 {
		return Registration{}, ErrInvalidDeviceName
	}
	if !validDeviceType(deviceType) {
		return Registration{}, fmt.Errorf("%w: %s", ErrInvalidDeviceType, deviceType)
	}

	token, err := newToken()
	if err != nil {
		return Registration{}, err
	}
	device := Device{ID: uuid.NewString(), Name: name, DeviceType: deviceType}
	if err := service.store.Create(ctx, device, hashToken(token), service.now()); err != nil {
		return Registration{}, fmt.Errorf("create device: %w", err)
	}
	return Registration{Device: device, Token: token}, nil
}

// Authenticate validates a bearer token and records activity for its device.
func (service *Service) Authenticate(ctx context.Context, token string) (Device, error) {
	if strings.TrimSpace(token) == "" {
		return Device{}, ErrInvalidToken
	}
	computedHash := hashToken(token)
	device, storedHash, err := service.store.FindByTokenHash(ctx, computedHash)
	if err != nil {
		return Device{}, fmt.Errorf("find device by token: %w", err)
	}
	if subtle.ConstantTimeCompare([]byte(computedHash), []byte(storedHash)) != 1 {
		return Device{}, ErrInvalidToken
	}
	if err := service.store.UpdateLastSeen(ctx, device.ID, service.now()); err != nil {
		return Device{}, fmt.Errorf("update device last seen: %w", err)
	}
	return device, nil
}

// List returns all registered devices.
func (service *Service) List(ctx context.Context) ([]DeviceSummary, error) {
	return service.store.List(ctx)
}

// Delete unregisters and deletes a device. The host admin device cannot be deleted.
func (service *Service) Delete(ctx context.Context, id string) error {
	if service.admin.Device.ID == id {
		return ErrCannotDeleteAdmin
	}
	return service.store.Delete(ctx, id)
}

func newToken() (string, error) {
	bytes := make([]byte, bearerTokenBytes)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("generate device token: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(bytes), nil
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func validDeviceType(deviceType string) bool {
	switch deviceType {
	case "ios", "android", "mac", "web":
		return true
	default:
		return false
	}
}

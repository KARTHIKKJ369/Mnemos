package devices

import (
	"context"
	"testing"
	"time"
)

func TestRegisterCreatesHashedCredential(t *testing.T) {
	t.Parallel()

	store := &memoryStore{}
	service := NewService(store)
	registration, err := service.Register(context.Background(), "Karthik's iPhone", "ios")
	if err != nil {
		t.Fatalf("Register() error = %v", err)
	}
	if registration.Device.ID == "" || registration.Token == "" {
		t.Fatal("registration did not include device identity and token")
	}
	if store.tokenHash == registration.Token {
		t.Fatal("raw token was persisted instead of its hash")
	}
	if store.tokenHash != hashToken(registration.Token) {
		t.Fatal("persisted token hash does not match returned token")
	}
}

func TestRegisterRejectsUnsupportedDeviceType(t *testing.T) {
	t.Parallel()

	service := NewService(&memoryStore{})
	if _, err := service.Register(context.Background(), "Desktop", "windows"); err == nil {
		t.Fatal("Register() error = nil, want invalid device type")
	}
}

func TestRegisterTrimsAndValidatesDeviceNameLength(t *testing.T) {
	t.Parallel()

	store := &memoryStore{}
	service := NewService(store)
	registration, err := service.Register(context.Background(), "  Phone  ", "ios")
	if err != nil {
		t.Fatalf("Register() error = %v", err)
	}
	if registration.Device.Name != "Phone" {
		t.Fatalf("device name = %q, want trimmed name", registration.Device.Name)
	}
	if _, err := service.Register(context.Background(), string(make([]rune, 101)), "ios"); err == nil {
		t.Fatal("Register() error = nil, want name-length validation error")
	}
}

type memoryStore struct {
	device    Device
	tokenHash string
}

func (store *memoryStore) Create(_ context.Context, device Device, tokenHash string, _ time.Time) error {
	store.device = device
	store.tokenHash = tokenHash
	return nil
}

func (store *memoryStore) FindByTokenHash(_ context.Context, tokenHash string) (Device, string, error) {
	if tokenHash != store.tokenHash {
		return Device{}, "", ErrInvalidToken
	}
	return store.device, store.tokenHash, nil
}

func (store *memoryStore) UpdateLastSeen(_ context.Context, _ string, _ time.Time) error {
	return nil
}

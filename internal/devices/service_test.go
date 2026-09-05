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

func (store *memoryStore) List(_ context.Context) ([]DeviceSummary, error) {
	if store.device.ID == "" {
		return []DeviceSummary{}, nil
	}
	return []DeviceSummary{
		{
			ID:         store.device.ID,
			Name:       store.device.Name,
			DeviceType: store.device.DeviceType,
		},
	}, nil
}

func (store *memoryStore) Delete(_ context.Context, id string) error {
	if store.device.ID == id {
		store.device = Device{}
		store.tokenHash = ""
	}
	return nil
}

func TestListDevices(t *testing.T) {
	t.Parallel()

	store := &memoryStore{}
	service := NewService(store)
	_, err := service.Register(context.Background(), "My Mac", "mac")
	if err != nil {
		t.Fatalf("Register() error = %v", err)
	}

	devices, err := service.List(context.Background())
	if err != nil {
		t.Fatalf("List() error = %v", err)
	}
	if len(devices) != 1 {
		t.Fatalf("expected 1 device, got %d", len(devices))
	}
	if devices[0].Name != "My Mac" {
		t.Fatalf("expected device name 'My Mac', got %q", devices[0].Name)
	}
}

func TestDeleteDevice(t *testing.T) {
	t.Parallel()

	store := &memoryStore{}
	service := NewService(store)
	reg, err := service.Register(context.Background(), "To Delete", "ios")
	if err != nil {
		t.Fatalf("Register() error = %v", err)
	}

	if err := service.Delete(context.Background(), reg.Device.ID); err != nil {
		t.Fatalf("Delete() error = %v", err)
	}

	devices, err := service.List(context.Background())
	if err != nil {
		t.Fatalf("List() error = %v", err)
	}
	if len(devices) != 0 {
		t.Fatalf("expected 0 devices after deletion, got %d", len(devices))
	}
}

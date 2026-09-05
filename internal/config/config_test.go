package config_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"photovault/internal/config"
)

func TestLoadDotEnv(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	content := `# Header comment
PHOTOVAULT_HTTP_ADDRESS=0.0.0.0:9090
# Another comment
PHOTOVAULT_STORAGE_PATH="/Volumes/External Drive/Vault"
export PHOTOVAULT_LOG_LEVEL='debug'
EMPTY_VAL=
`
	if err := os.WriteFile(envPath, []byte(content), 0644); err != nil {
		t.Fatalf("failed to write test .env: %v", err)
	}

	vars, err := config.LoadDotEnv(envPath)
	if err != nil {
		t.Fatalf("LoadDotEnv returned error: %v", err)
	}

	if vars["PHOTOVAULT_HTTP_ADDRESS"] != "0.0.0.0:9090" {
		t.Errorf("expected 0.0.0.0:9090, got %q", vars["PHOTOVAULT_HTTP_ADDRESS"])
	}
	if vars["PHOTOVAULT_STORAGE_PATH"] != "/Volumes/External Drive/Vault" {
		t.Errorf("expected /Volumes/External Drive/Vault, got %q", vars["PHOTOVAULT_STORAGE_PATH"])
	}
	if vars["PHOTOVAULT_LOG_LEVEL"] != "debug" {
		t.Errorf("expected debug, got %q", vars["PHOTOVAULT_LOG_LEVEL"])
	}
}

func TestApplyDotEnv_DoesNotOverrideOS(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	const testKey = "PHOTOVAULT_TEST_OVERRIDE_KEY"
	t.Setenv(testKey, "from_os")

	content := testKey + "=from_env\nPHOTOVAULT_TEST_UNSET=unset_val\n"
	if err := os.WriteFile(envPath, []byte(content), 0644); err != nil {
		t.Fatalf("failed to write test .env: %v", err)
	}

	if err := config.ApplyDotEnv(envPath); err != nil {
		t.Fatalf("ApplyDotEnv failed: %v", err)
	}

	if os.Getenv(testKey) != "from_os" {
		t.Errorf("expected OS env to take precedence, got %q", os.Getenv(testKey))
	}
	if os.Getenv("PHOTOVAULT_TEST_UNSET") != "unset_val" {
		t.Errorf("expected unset val to be loaded from .env, got %q", os.Getenv("PHOTOVAULT_TEST_UNSET"))
	}
}

func TestUpdateDotEnv_NewAndMutate(t *testing.T) {
	tmpDir := t.TempDir()
	envPath := filepath.Join(tmpDir, ".env")

	// 1. Create from scratch
	if err := config.UpdateDotEnv(envPath, "PHOTOVAULT_STORAGE_PATH", "/first/path"); err != nil {
		t.Fatalf("UpdateDotEnv failed: %v", err)
	}

	vars, err := config.LoadDotEnv(envPath)
	if err != nil {
		t.Fatalf("LoadDotEnv failed: %v", err)
	}
	if vars["PHOTOVAULT_STORAGE_PATH"] != "/first/path" {
		t.Errorf("expected /first/path, got %q", vars["PHOTOVAULT_STORAGE_PATH"])
	}

	// 2. Add second variable
	if err := config.UpdateDotEnv(envPath, "PHOTOVAULT_HTTP_ADDRESS", "0.0.0.0:8080"); err != nil {
		t.Fatalf("UpdateDotEnv failed: %v", err)
	}

	// 3. Mutate first variable with spaces (should be quoted)
	if err := config.UpdateDotEnv(envPath, "PHOTOVAULT_STORAGE_PATH", "/Volumes/My Drive/Photos"); err != nil {
		t.Fatalf("UpdateDotEnv failed: %v", err)
	}

	data, err := os.ReadFile(envPath)
	if err != nil {
		t.Fatalf("read .env failed: %v", err)
	}
	content := string(data)
	if !strings.Contains(content, `PHOTOVAULT_STORAGE_PATH="/Volumes/My Drive/Photos"`) {
		t.Errorf("expected quoted path in .env, got:\n%s", content)
	}
	if !strings.Contains(content, `PHOTOVAULT_HTTP_ADDRESS=0.0.0.0:8080`) {
		t.Errorf("expected other variable preserved, got:\n%s", content)
	}

	// Verify loaded correctly
	vars, err = config.LoadDotEnv(envPath)
	if err != nil {
		t.Fatalf("LoadDotEnv failed: %v", err)
	}
	if vars["PHOTOVAULT_STORAGE_PATH"] != "/Volumes/My Drive/Photos" {
		t.Errorf("expected unquoted parsed value, got %q", vars["PHOTOVAULT_STORAGE_PATH"])
	}
}

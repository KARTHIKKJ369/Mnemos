package syncclient

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"testing"
)

func TestSyncOnceResumesVerifiesAndAcknowledges(t *testing.T) {
	content := []byte("resumable content")
	sum := sha256.Sum256(content)
	file := File{ID: "file-1", Hash: hex.EncodeToString(sum[:]), Filename: "photo.jpg", MIMEType: "image/jpeg", Size: int64(len(content)), UploadedAt: 10}
	var ranges []string
	var acknowledgements [][]string
	var mutex sync.Mutex
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/sync/diff":
			if request.URL.Query().Get("since") == "10" {
				json.NewEncoder(writer).Encode(map[string]any{"files": []File{}})
				return
			}
			json.NewEncoder(writer).Encode(map[string]any{"files": []File{file}, "next_since": 10})
		case "/media/file-1/original":
			mutex.Lock()
			ranges = append(ranges, request.Header.Get("Range"))
			mutex.Unlock()
			if request.Header.Get("Range") == "bytes=5-" {
				writer.WriteHeader(http.StatusPartialContent)
				writer.Write(content[5:])
				return
			}
			writer.Write(content)
		case "/sync/ack":
			var input struct {
				IDs []string `json:"file_ids"`
			}
			json.NewDecoder(request.Body).Decode(&input)
			mutex.Lock()
			acknowledgements = append(acknowledgements, input.IDs)
			mutex.Unlock()
			json.NewEncoder(writer).Encode(map[string]int{"acknowledged": len(input.IDs)})
		default:
			http.NotFound(writer, request)
		}
	}))
	defer server.Close()
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "file-1.part"), content[:5], 0o600); err != nil {
		t.Fatal(err)
	}
	client, err := New(Config{BaseURL: server.URL, Token: "token", DatabasePath: filepath.Join(root, "sync.db"), DownloadDir: filepath.Join(root, "downloads"), TemporaryDir: root})
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	if err := client.SyncOnce(context.Background()); err != nil {
		t.Fatalf("sync: %v", err)
	}
	if len(ranges) != 1 || ranges[0] != "bytes=5-" {
		t.Fatalf("ranges = %v", ranges)
	}
	if len(acknowledgements) != 1 || len(acknowledgements[0]) != 1 || acknowledgements[0][0] != "file-1" {
		t.Fatalf("acknowledgements = %v", acknowledgements)
	}
	stored, err := os.ReadFile(filepath.Join(root, "downloads", "file-1-photo.jpg"))
	if err != nil {
		t.Fatal(err)
	}
	if string(stored) != string(content) {
		t.Fatalf("download = %q", stored)
	}
}

func TestSyncOnceDoesNotAcknowledgeHashMismatch(t *testing.T) {
	var acknowledged bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/sync/diff":
			json.NewEncoder(w).Encode(map[string]any{"files": []File{{ID: "bad", Hash: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Filename: "bad.bin", Size: 3}}})
		case "/media/bad/original":
			w.Write([]byte("bad"))
		case "/sync/ack":
			acknowledged = true
		}
	}))
	defer server.Close()
	root := t.TempDir()
	client, err := New(Config{BaseURL: server.URL, Token: "token", DatabasePath: filepath.Join(root, "sync.db"), DownloadDir: filepath.Join(root, "downloads"), Retry: RetryPolicy{MaxAttempts: 1}})
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	if err := client.SyncOnce(context.Background()); err == nil {
		t.Fatal("expected hash mismatch")
	}
	if acknowledged {
		t.Fatal("acknowledged corrupt download")
	}
	if _, err := os.Stat(filepath.Join(root, "downloads", "bad-bad.bin")); !os.IsNotExist(err) {
		t.Fatalf("corrupt output exists: %v", err)
	}
}

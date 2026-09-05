package mediaindex

import (
	"context"
	"fmt"
	"path/filepath"
	"testing"
	"time"

	"photovault/internal/database"
	"photovault/internal/files"
)

func TestSearchFavoriteAndSoftDelete(t *testing.T) {
	db, err := database.Open(context.Background(), filepath.Join(t.TempDir(), "index.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	repo := NewRepository(db)
	if _, err := db.Exec("PRAGMA foreign_keys = OFF"); err != nil {
		t.Fatal(err)
	}
	t1 := time.Date(2023, 5, 10, 14, 0, 0, 0, time.UTC)
	for _, tc := range []struct {
		f    files.File
		meta ExtractedMetadata
	}{
		{
			f:    files.File{ID: "one", OriginalFilename: "Beach Sunset.jpg", MIMEType: "image/jpeg", SizeBytes: 10, UploadedAt: time.UnixMilli(1000)},
			meta: ExtractedMetadata{TakenAt: &t1},
		},
		{
			f:    files.File{ID: "two", OriginalFilename: "Receipt.png", MIMEType: "image/png", SizeBytes: 20, UploadedAt: time.UnixMilli(2000)},
			meta: ExtractedMetadata{},
		},
	} {
		if err := repo.Upsert(context.Background(), tc.f, tc.meta); err != nil {
			t.Fatal(err)
		}
	}
	items, err := repo.Search(context.Background(), Search{Query: "beach", Limit: 10})
	if err != nil || len(items) != 1 || items[0].FileID != "one" {
		t.Fatalf("search = %+v, %v", items, err)
	}
	if items[0].TakenAt == nil || !items[0].TakenAt.Equal(t1) {
		t.Fatalf("expected taken_at %v, got %v", t1, items[0].TakenAt)
	}

	// Test sort by taken_at
	sortedItems, err := repo.Search(context.Background(), Search{Sort: "taken_at", Order: "desc", Limit: 10})
	if err != nil || len(sortedItems) != 2 {
		t.Fatalf("sort taken_at = %+v, %v", sortedItems, err)
	}
	// "two" has uploaded_at 2000 ms, "one" has taken_at 2023 (which is much later than 2000ms), so "one" should be first in desc
	if sortedItems[0].FileID != "one" {
		t.Fatalf("expected first item 'one', got %s", sortedItems[0].FileID)
	}

	// Test EnqueueMissingMetadata
	enqueued, err := repo.EnqueueMissingMetadata(context.Background())
	if err != nil {
		t.Fatalf("EnqueueMissingMetadata error: %v", err)
	}
	if enqueued != 1 { // Only "two" has taken_at == nil
		t.Fatalf("expected 1 item enqueued, got %d", enqueued)
	}

	if err := repo.SetFavorite(context.Background(), "one", true); err != nil {
		t.Fatal(err)
	}
	favorite := true
	items, err = repo.Search(context.Background(), Search{Favorite: &favorite, Limit: 10})
	if err != nil || len(items) != 1 {
		t.Fatalf("favorites = %+v, %v", items, err)
	}
	if err := repo.SoftDelete(context.Background(), "one"); err != nil {
		t.Fatal(err)
	}
	items, err = repo.Search(context.Background(), Search{Limit: 10})
	if err != nil || len(items) != 1 || items[0].FileID != "two" {
		t.Fatalf("after delete = %+v, %v", items, err)
	}
}

func BenchmarkIndexedFilenameSearch(b *testing.B) {
	db, err := database.Open(context.Background(), filepath.Join(b.TempDir(), "index.db"))
	if err != nil {
		b.Fatal(err)
	}
	defer db.Close()
	repo := NewRepository(db)
	if _, err := db.Exec("PRAGMA foreign_keys = OFF"); err != nil {
		b.Fatal(err)
	}
	for i := 0; i < 10000; i++ {
		f := files.File{ID: fmt.Sprintf("%d", i), OriginalFilename: fmt.Sprintf("holiday-beach-%d.jpg", i), MIMEType: "image/jpeg", UploadedAt: time.Now()}
		if err := repo.Upsert(context.Background(), f, ExtractedMetadata{}); err != nil {
			b.Fatal(err)
		}
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		if _, err := repo.Search(context.Background(), Search{Query: "beach-999", Limit: 50}); err != nil {
			b.Fatal(err)
		}
	}
}


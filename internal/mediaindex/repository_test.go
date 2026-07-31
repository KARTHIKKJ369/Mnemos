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
	for _, f := range []files.File{{ID: "one", OriginalFilename: "Beach Sunset.jpg", MIMEType: "image/jpeg", SizeBytes: 10, UploadedAt: time.UnixMilli(1000)}, {ID: "two", OriginalFilename: "Receipt.png", MIMEType: "image/png", SizeBytes: 20, UploadedAt: time.UnixMilli(2000)}} {
		if err := repo.Upsert(context.Background(), f, nil, nil); err != nil {
			t.Fatal(err)
		}
	}
	items, err := repo.Search(context.Background(), Search{Query: "beach", Limit: 10})
	if err != nil || len(items) != 1 || items[0].FileID != "one" {
		t.Fatalf("search = %+v, %v", items, err)
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
		if err := repo.Upsert(context.Background(), f, nil, nil); err != nil {
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

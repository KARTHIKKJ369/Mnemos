package httpapi

import (
	"io/fs"
	"net/http"
	"strings"
)

// NewSPAHandler returns an http.Handler that serves a pre-built Single Page
// Application from the provided fs.FS.
//
// Rules:
//   - Paths that start with any apiPrefix are NOT handled here (they fall
//     through to the API router).
//   - Requests for files that exist in the embedded FS (js, css, assets) are
//     served directly with long-lived cache headers.
//   - Everything else (unknown paths, deep links) serves index.html so that
//     the React router handles client-side navigation.
func NewSPAHandler(staticFS fs.FS) http.Handler {
	fileServer := http.FileServer(http.FS(staticFS))

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path := strings.TrimPrefix(r.URL.Path, "/")

		// Try to open the requested path in the embedded FS.
		f, err := staticFS.Open(path)
		if err != nil {
			// File not found — serve index.html (SPA fallback).
			serveSPAIndex(w, r, staticFS)
			return
		}
		defer f.Close()

		stat, err := f.Stat()
		if err != nil || stat.IsDir() {
			// Directory listing disabled — serve index.html.
			serveSPAIndex(w, r, staticFS)
			return
		}

		// Cache immutable hashed assets (Vite appends content hashes).
		if strings.HasPrefix(path, "assets/") {
			w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		}

		fileServer.ServeHTTP(w, r)
	})
}

func serveSPAIndex(w http.ResponseWriter, r *http.Request, staticFS fs.FS) {
	data, err := fs.ReadFile(staticFS, "index.html")
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
	_, _ = w.Write(data)
}

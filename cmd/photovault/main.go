// Command photovault starts the PhotoVault HTTP server.
// In production it also serves the embedded React SPA on the same port.
package main

import (
	"context"
	"embed"
	"errors"
	"flag"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"photovault/internal/app"
	"photovault/internal/config"
	"photovault/internal/httpapi"
)

// webFS holds the pre-built React app from mnemos-web/dist.
// The directory must exist at compile time; run `pnpm run build` first.
// The `all:` prefix is required so that Vite's code-split chunks (whose
// filenames begin with `_`, e.g. _app-XXX.js) are included in the embed.
//
//go:embed all:web/dist
var webFS embed.FS

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	var storageFlag, addrFlag string
	flag.StringVar(&storageFlag, "storage", "", "custom storage folder location")
	flag.StringVar(&storageFlag, "storage-path", "", "custom storage folder location")
	flag.StringVar(&addrFlag, "addr", "", "HTTP listen address (e.g. 0.0.0.0:8080)")
	flag.Parse()

	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load configuration: %w", err)
	}
	if storageFlag != "" {
		cfg.StoragePath = filepath.Clean(storageFlag)
		cfg.DatabasePath = filepath.Join(cfg.StoragePath, "vault.db")
	}
	if addrFlag != "" {
		cfg.HTTPAddress = addrFlag
	}

	// Strip the leading "web/" prefix so the FS root is the dist directory.
	distFS, err := fs.Sub(webFS, "web/dist")
	if err != nil {
		return fmt.Errorf("prepare embedded web assets: %w", err)
	}
	spaHandler := httpapi.NewSPAHandler(distFS)

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel}))
	application, err := app.New(cfg, logger, spaHandler)
	if err != nil {
		return fmt.Errorf("create application: %w", err)
	}
	defer func() {
		if closeErr := application.Close(); closeErr != nil {
			logger.Error("close application", "error", closeErr)
		}
	}()

	serverErr := make(chan error, 1)
	go func() {
		logger.Info("server started", "address", cfg.HTTPAddress)
		serverErr <- application.Serve()
	}()

	signalContext, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	select {
	case <-signalContext.Done():
		logger.Info("shutdown signal received")
		shutdownContext, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
		defer cancel()
		if err := application.Shutdown(shutdownContext); err != nil {
			return fmt.Errorf("graceful shutdown: %w", err)
		}
		if err := <-serverErr; err != nil && !errors.Is(err, context.Canceled) {
			return fmt.Errorf("serve HTTP: %w", err)
		}
	case err := <-serverErr:
		return fmt.Errorf("serve HTTP: %w", err)
	}

	return nil
}


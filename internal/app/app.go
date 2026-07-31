// Package app wires PhotoVault infrastructure into a runnable application.
package app

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"photovault/internal/config"
	"photovault/internal/database"
	"photovault/internal/devices"
	"photovault/internal/files"
	"photovault/internal/httpapi"
	"photovault/internal/ratelimit"
	"photovault/internal/storage"
	"photovault/internal/uploads"
)

const databaseInitializationTimeout = 10 * time.Second

// App is the PhotoVault HTTP application and its managed resources.
type App struct {
	database *sql.DB
	server   *http.Server
}

// New constructs a PhotoVault application, ensuring storage and database schema are ready.
func New(cfg config.Config, logger *slog.Logger) (*App, error) {
	layout, err := storage.Ensure(cfg.StoragePath)
	if err != nil {
		return nil, fmt.Errorf("prepare storage: %w", err)
	}

	startupContext, cancel := context.WithTimeout(context.Background(), databaseInitializationTimeout)
	defer cancel()
	db, err := database.Open(startupContext, cfg.DatabasePath)
	if err != nil {
		return nil, fmt.Errorf("initialize database: %w", err)
	}

	deviceService := devices.NewService(devices.NewSQLiteStore(db))
	uploadHandler := uploads.NewHandler(storage.NewBlobStore(layout), files.NewStore(db), cfg.MaxUploadBytes, layout.Blobs, logger)
	return &App{
		database: db,
		server: &http.Server{
			Addr:              cfg.HTTPAddress,
			Handler:           httpapi.NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler),
			ReadHeaderTimeout: 10 * time.Second,
			IdleTimeout:       60 * time.Second,
		},
	}, nil
}

// Serve starts accepting HTTP requests until the server is shut down or fails.
func (application *App) Serve() error {
	err := application.server.ListenAndServe()
	if err == http.ErrServerClosed {
		return context.Canceled
	}
	return err
}

// Shutdown gracefully stops the HTTP server.
func (application *App) Shutdown(ctx context.Context) error {
	return application.server.Shutdown(ctx)
}

// Close releases the application's database connection.
func (application *App) Close() error {
	return application.database.Close()
}

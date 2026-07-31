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
	"photovault/internal/synchronization"
	"photovault/internal/uploads"
)

const databaseInitializationTimeout = 10 * time.Second

// App is the PhotoVault HTTP application and its managed resources.
type App struct {
	database       *sql.DB
	syncRepository *synchronization.SyncRepository
	server         *http.Server
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

	syncRepository, err := synchronization.NewSyncRepository(startupContext, db)
	if err != nil {
		db.Close()
		return nil, fmt.Errorf("prepare sync repository: %w", err)
	}
	syncService, err := synchronization.NewSyncService(syncRepository, cfg.SyncDefaultLimit, cfg.SyncMaxLimit, cfg.SyncAckMaxBatch)
	if err != nil {
		syncRepository.Close()
		db.Close()
		return nil, fmt.Errorf("create sync service: %w", err)
	}

	deviceService := devices.NewService(devices.NewSQLiteStore(db))
	fileRepository := files.NewRepository(db)
	uploadHandler := uploads.NewHandler(storage.NewBlobStore(layout), fileRepository, cfg.MaxUploadBytes, layout.Blobs, logger)
	fileHandler := httpapi.NewFileHandler(files.NewService(fileRepository, files.NewLRUExistenceCache(cfg.HashCacheSize, cfg.HashCacheTTL)), logger)
	syncHandler := httpapi.NewSyncHandler(syncService, logger)
	return &App{
		database:       db,
		syncRepository: syncRepository,
		server: &http.Server{
			Addr:              cfg.HTTPAddress,
			Handler:           httpapi.NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler, http.HandlerFunc(fileHandler.Exists), syncHandler),
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

// Close releases the application's database connection and prepared statements.
func (application *App) Close() error {
	if application.syncRepository != nil {
		if err := application.syncRepository.Close(); err != nil {
			return fmt.Errorf("close sync repository: %w", err)
		}
	}
	return application.database.Close()
}

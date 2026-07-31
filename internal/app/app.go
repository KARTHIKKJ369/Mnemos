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
	"photovault/internal/mediaindex"
	"photovault/internal/observability"
	"photovault/internal/processing"
	"photovault/internal/ratelimit"
	"photovault/internal/storage"
	"photovault/internal/synchronization"
	"photovault/internal/uploads"
	"photovault/internal/vaults"
)

const databaseInitializationTimeout = 10 * time.Second

// App is the PhotoVault HTTP application and its managed resources.
type App struct {
	database       *sql.DB
	syncRepository *synchronization.SyncRepository
	server         *http.Server
	worker         *processing.Worker
	indexWorker    *mediaindex.Worker
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
	blobStore := storage.NewBlobStore(layout)
	processingRepository := processing.NewRepository(db)
	indexRepository := mediaindex.NewRepository(db)
	indexWorker := mediaindex.NewWorker(indexRepository, fileRepository, blobStore, logger)
	indexWorker.Start(context.Background())
	mediaWorker := processing.NewWorker(processingRepository, processing.NewMediaProcessor(blobStore, layout), logger)
	if err := mediaWorker.Start(context.Background()); err != nil {
		syncRepository.Close()
		db.Close()
		return nil, fmt.Errorf("start media worker: %w", err)
	}
	uploadHandler := uploads.NewHandler(blobStore, fileRepository, cfg.MaxUploadBytes, layout.Blobs, logger, processingRepository, indexRepository)
	fileHandler := httpapi.NewFileHandler(files.NewService(fileRepository, files.NewLRUExistenceCache(cfg.HashCacheSize, cfg.HashCacheTTL)), logger)
	mediaHandler := httpapi.NewMediaHandler(files.NewMediaService(fileRepository, blobStore), logger)
	indexHandler := httpapi.NewIndexHandler(indexRepository)
	vaultHandler := httpapi.NewVaultHandler(vaults.NewRepository(db))
	metrics := observability.NewMetrics()
	operationsHandler := httpapi.NewOperationsHandler(observability.Health{Database: db, StoragePath: layout.Root, Version: "dev", Commit: "unknown", StartedAt: time.Now(), Workers: func() bool { return true }}, metrics)
	syncHandler := httpapi.NewSyncHandler(syncService, logger)
	return &App{
		database:       db,
		syncRepository: syncRepository,
		worker:         mediaWorker,
		indexWorker:    indexWorker,
		server: &http.Server{
			Addr:              cfg.HTTPAddress,
			Handler:           httpapi.NewRouter(logger, deviceService, ratelimit.NewRegistrationLimiter(), deviceService, uploadHandler, http.HandlerFunc(fileHandler.Exists), syncHandler, mediaHandler, indexHandler, operationsHandler, vaultHandler),
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
	serverErr := application.server.Shutdown(ctx)
	workerErr := application.worker.Stop(ctx)
	indexErr := application.indexWorker.Stop(ctx)
	if serverErr != nil {
		return serverErr
	}
	if workerErr != nil {
		return fmt.Errorf("stop media worker: %w", workerErr)
	}
	if indexErr != nil {
		return fmt.Errorf("stop index worker: %w", indexErr)
	}
	return nil
}

// Close releases the application's database connection and prepared statements.
func (application *App) Close() error {
	if application.worker != nil {
		stopContext, cancel := context.WithTimeout(context.Background(), databaseInitializationTimeout)
		defer cancel()
		if err := application.worker.Stop(stopContext); err != nil {
			return fmt.Errorf("stop media worker during close: %w", err)
		}
	}
	if application.indexWorker != nil {
		stopContext, cancel := context.WithTimeout(context.Background(), databaseInitializationTimeout)
		defer cancel()
		if err := application.indexWorker.Stop(stopContext); err != nil {
			return fmt.Errorf("stop index worker during close: %w", err)
		}
	}
	if application.syncRepository != nil {
		if err := application.syncRepository.Close(); err != nil {
			return fmt.Errorf("close sync repository: %w", err)
		}
	}
	return application.database.Close()
}

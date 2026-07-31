// Command photovault starts the PhotoVault HTTP server.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"photovault/internal/app"
	"photovault/internal/config"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load configuration: %w", err)
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel}))
	application, err := app.New(cfg, logger)
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

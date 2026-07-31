// Package syncclient provides a reusable, durable PhotoVault client synchronization engine.
package syncclient

import (
	"log/slog"
	"time"
)

type File struct {
	ID         string `json:"file_id"`
	Hash       string `json:"hash"`
	Filename   string `json:"filename"`
	MIMEType   string `json:"mime_type"`
	Size       int64  `json:"size_bytes"`
	UploadedAt int64  `json:"uploaded_at"`
}

type EventType string

const (
	DownloadStarted      EventType = "download_started"
	BytesDownloaded      EventType = "bytes_downloaded"
	DownloadCompleted    EventType = "completed"
	DownloadRetrying     EventType = "retrying"
	DownloadFailed       EventType = "failed"
	DownloadVerified     EventType = "verified"
	DownloadAcknowledged EventType = "acknowledged"
)

type Event struct {
	Type                        EventType
	FileID                      string
	BytesDownloaded, TotalBytes int64
	Attempt                     int
	Err                         error
}
type RetryPolicy struct {
	MaxAttempts                int
	InitialBackoff, MaxBackoff time.Duration
}
type Config struct {
	BaseURL, Token, DatabasePath, DownloadDir, TemporaryDir string
	SyncInterval, HTTPTimeout                               time.Duration
	MaxConcurrentDownloads, PageSize                        int
	Retry                                                   RetryPolicy
	OnEvent                                                 func(Event)
	Logger                                                  *slog.Logger
}

func (c Config) normalized() Config {
	if c.MaxConcurrentDownloads <= 0 {
		c.MaxConcurrentDownloads = 4
	}
	if c.PageSize <= 0 {
		c.PageSize = 100
	}
	if c.HTTPTimeout <= 0 {
		c.HTTPTimeout = 60 * time.Second
	}
	if c.SyncInterval <= 0 {
		c.SyncInterval = time.Minute
	}
	if c.Retry.MaxAttempts <= 0 {
		c.Retry.MaxAttempts = 5
	}
	if c.Retry.InitialBackoff <= 0 {
		c.Retry.InitialBackoff = time.Second
	}
	if c.Retry.MaxBackoff <= 0 {
		c.Retry.MaxBackoff = 30 * time.Second
	}
	if c.Logger == nil {
		c.Logger = slog.Default()
	}
	return c
}

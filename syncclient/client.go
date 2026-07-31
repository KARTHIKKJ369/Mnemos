package syncclient

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type Client struct {
	cfg        Config
	repository *repository
	http       *http.Client
}

func New(cfg Config) (*Client, error) {
	cfg = cfg.normalized()
	if cfg.BaseURL == "" || cfg.Token == "" || cfg.DatabasePath == "" || cfg.DownloadDir == "" {
		return nil, errors.New("base URL, token, database path, and download directory are required")
	}
	if cfg.TemporaryDir == "" {
		cfg.TemporaryDir = cfg.DownloadDir
	}
	for _, dir := range []string{cfg.DownloadDir, cfg.TemporaryDir} {
		if err := os.MkdirAll(dir, 0o750); err != nil {
			return nil, fmt.Errorf("create sync directory: %w", err)
		}
	}
	r, err := openRepository(cfg.DatabasePath)
	if err != nil {
		return nil, err
	}
	return &Client{cfg: cfg, repository: r, http: &http.Client{Timeout: cfg.HTTPTimeout}}, nil
}
func (c *Client) Close() error { return c.repository.Close() }
func (c *Client) emit(event Event) {
	if c.cfg.OnEvent != nil {
		c.cfg.OnEvent(event)
	}
}
func (c *Client) Run(ctx context.Context) error {
	ticker := time.NewTicker(c.cfg.SyncInterval)
	defer ticker.Stop()
	for {
		if err := c.SyncOnce(ctx); err != nil && ctx.Err() == nil {
			c.cfg.Logger.Warn("client synchronization failed", "error", err)
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}
func (c *Client) SyncOnce(ctx context.Context) error {
	for {
		since, err := c.repository.cursor(ctx)
		if err != nil {
			return fmt.Errorf("read sync cursor: %w", err)
		}
		page, next, err := c.diff(ctx, since)
		if err != nil {
			return err
		}
		if len(page) == 0 {
			return nil
		}
		if err := c.processPage(ctx, page); err != nil {
			return err
		}
		if next == nil {
			return nil
		}
		if err := c.repository.setCursor(ctx, *next); err != nil {
			return fmt.Errorf("advance sync cursor: %w", err)
		}
	}
}
func (c *Client) diff(ctx context.Context, since *int64) ([]File, *int64, error) {
	u, err := url.Parse(c.cfg.BaseURL + "/sync/diff")
	if err != nil {
		return nil, nil, err
	}
	q := u.Query()
	q.Set("limit", fmt.Sprint(c.cfg.PageSize))
	if since != nil {
		q.Set("since", fmt.Sprint(*since))
	}
	u.RawQuery = q.Encode()
	req, err := c.request(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, nil, err
	}
	res, err := c.http.Do(req)
	if err != nil {
		return nil, nil, fmt.Errorf("sync diff: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return nil, nil, fmt.Errorf("sync diff status: %s", res.Status)
	}
	var body struct {
		Files     []File `json:"files"`
		NextSince *int64 `json:"next_since"`
	}
	if err := json.NewDecoder(res.Body).Decode(&body); err != nil {
		return nil, nil, err
	}
	return body.Files, body.NextSince, nil
}
func (c *Client) processPage(ctx context.Context, files []File) error {
	jobs := make(chan File)
	done := make(chan File, len(files))
	errs := make(chan error, 1)
	var wg sync.WaitGroup
	for range c.cfg.MaxConcurrentDownloads {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for f := range jobs {
				if err := c.downloadWithRetry(ctx, f); err != nil {
					select {
					case errs <- err:
					default:
					}
					continue
				}
				done <- f
			}
		}()
	}
	for _, f := range files {
		jobs <- f
	}
	close(jobs)
	wg.Wait()
	close(done)
	select {
	case err := <-errs:
		return err
	default:
	}
	ids := make([]string, 0, len(files))
	for f := range done {
		ids = append(ids, f.ID)
	}
	if len(ids) != len(files) {
		return errors.New("sync page contains unfinished downloads")
	}
	if err := c.ack(ctx, ids); err != nil {
		return err
	}
	if err := c.repository.markAcknowledged(ctx, ids); err != nil {
		return err
	}
	for _, id := range ids {
		c.emit(Event{Type: DownloadAcknowledged, FileID: id})
	}
	return nil
}
func (c *Client) downloadWithRetry(ctx context.Context, f File) error {
	var err error
	delay := c.cfg.Retry.InitialBackoff
	for attempt := 1; attempt <= c.cfg.Retry.MaxAttempts; attempt++ {
		err = c.download(ctx, f)
		if err == nil {
			return nil
		}
		if attempt < c.cfg.Retry.MaxAttempts {
			c.cfg.Logger.Warn("client download failed; retrying", "file_id", f.ID, "attempt", attempt, "error", err)
			c.emit(Event{Type: DownloadRetrying, FileID: f.ID, Attempt: attempt, Err: err})
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(delay):
			}
			delay *= 2
			if delay > c.cfg.Retry.MaxBackoff {
				delay = c.cfg.Retry.MaxBackoff
			}
		}
	}
	c.emit(Event{Type: DownloadFailed, FileID: f.ID, Err: err})
	c.cfg.Logger.Error("client download failed", "file_id", f.ID, "error", err)
	return err
}
func (c *Client) download(ctx context.Context, f File) error {
	complete, err := c.repository.completed(ctx, f.ID)
	if err != nil {
		return err
	}
	if complete {
		return nil
	}
	safeName := filepath.Base(f.Filename)
	if safeName == "." || safeName == ".." || safeName == "" {
		safeName = f.ID
	}
	target := filepath.Join(c.cfg.DownloadDir, f.ID+"-"+safeName)
	partial := filepath.Join(c.cfg.TemporaryDir, f.ID+".part")
	var offset int64
	if info, err := os.Stat(partial); err == nil {
		offset = info.Size()
	} else if !os.IsNotExist(err) {
		return err
	}
	req, err := c.request(ctx, http.MethodGet, c.cfg.BaseURL+"/media/"+url.PathEscape(f.ID)+"/original", nil)
	if err != nil {
		return err
	}
	if offset > 0 {
		req.Header.Set("Range", fmt.Sprintf("bytes=%d-", offset))
	}
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK && res.StatusCode != http.StatusPartialContent {
		return fmt.Errorf("download status: %s", res.Status)
	}
	if offset > 0 && res.StatusCode == http.StatusOK {
		offset = 0
	}
	flag := os.O_CREATE | os.O_WRONLY
	if offset > 0 {
		flag |= os.O_APPEND
	} else {
		flag |= os.O_TRUNC
	}
	out, err := os.OpenFile(partial, flag, 0o600)
	if err != nil {
		return err
	}
	c.emit(Event{Type: DownloadStarted, FileID: f.ID, BytesDownloaded: offset, TotalBytes: f.Size})
	written, copyErr := io.Copy(out, io.TeeReader(res.Body, &progressWriter{start: offset, file: f, emit: c.emit}))
	closeErr := out.Close()
	if copyErr != nil {
		return copyErr
	}
	if closeErr != nil {
		return closeErr
	}
	if offset+written != f.Size {
		return fmt.Errorf("download size mismatch: got %d, want %d", offset+written, f.Size)
	}
	input, err := os.Open(partial)
	if err != nil {
		return err
	}
	sum := sha256.New()
	_, err = io.Copy(sum, input)
	input.Close()
	if err != nil {
		return err
	}
	if !strings.EqualFold(hex.EncodeToString(sum.Sum(nil)), f.Hash) {
		os.Remove(partial)
		return errors.New("download SHA-256 mismatch")
	}
	c.emit(Event{Type: DownloadVerified, FileID: f.ID, BytesDownloaded: f.Size, TotalBytes: f.Size})
	if err := os.Rename(partial, target); err != nil {
		return err
	}
	if err := c.repository.save(ctx, f, target, false); err != nil {
		return err
	}
	c.emit(Event{Type: DownloadCompleted, FileID: f.ID, BytesDownloaded: f.Size, TotalBytes: f.Size})
	return nil
}

type progressWriter struct {
	start int64
	file  File
	emit  func(Event)
}

func (p *progressWriter) Write(b []byte) (int, error) {
	p.start += int64(len(b))
	p.emit(Event{Type: BytesDownloaded, FileID: p.file.ID, BytesDownloaded: p.start, TotalBytes: p.file.Size})
	return len(b), nil
}
func (c *Client) ack(ctx context.Context, ids []string) error {
	body, err := json.Marshal(map[string][]string{"file_ids": ids})
	if err != nil {
		return err
	}
	req, err := c.request(ctx, http.MethodPost, c.cfg.BaseURL+"/sync/ack", strings.NewReader(string(body)))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("sync ack status: %s", res.Status)
	}
	return nil
}
func (c *Client) request(ctx context.Context, method, target string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, method, target, body)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.cfg.Token)
	return req, nil
}

// Package encrypteduploads implements opaque protocol-v2 upload sessions.
package encrypteduploads

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"github.com/google/uuid"
	"io"
	"os"
	"path/filepath"
	"time"
)

const ChunkSize = 4 << 20

var (
	ErrNotFound  = errors.New("upload not found")
	ErrForbidden = errors.New("vault membership required")
	ErrComplete  = errors.New("upload already complete")
)

type Service struct {
	db   *sql.DB
	root string
	ttl  time.Duration
}

func NewService(db *sql.DB, root string, ttl time.Duration) *Service {
	if ttl <= 0 {
		ttl = 24 * time.Hour
	}
	return &Service{db, root, ttl}
}

type Session struct {
	ID        string
	ExpiresAt time.Time
	Received  []int
}
type CompleteInput struct {
	ObjectID, CiphertextHash            string
	WrappedFileKey, Manifest, BaseNonce []byte
	ChunkCount, AlgorithmVersion        int
}

func (s *Service) Create(ctx context.Context, vault, device string) (Session, error) {
	if err := s.member(ctx, vault, device); err != nil {
		return Session{}, err
	}
	id := uuid.NewString()
	expires := time.Now().Add(s.ttl)
	_, err := s.db.ExecContext(ctx, "INSERT INTO encrypted_uploads(id,vault_id,device_id,state,created_at) VALUES(?,?,?,'open',?)", id, vault, device, time.Now().UnixMilli())
	if err != nil {
		return Session{}, fmt.Errorf("create encrypted upload: %w", err)
	}
	return Session{ID: id, ExpiresAt: expires}, nil
}
func (s *Service) PutChunk(ctx context.Context, vault, upload, device string, index int, nonce, tag []byte, length int64, body io.Reader) error {
	if index < 0 || length < 0 || len(nonce) != 12 || len(tag) != 16 {
		return errors.New("invalid chunk metadata")
	}
	if err := s.open(ctx, vault, upload, device); err != nil {
		return err
	}
	dir := filepath.Join(s.root, "blobs", "e2ee", vault, upload)
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return err
	}
	tmp, err := os.CreateTemp(dir, ".chunk-")
	if err != nil {
		return err
	}
	path := filepath.Join(dir, fmt.Sprintf("%08d.chunk", index))
	written, copyErr := io.Copy(tmp, io.LimitReader(body, length+1))
	closeErr := tmp.Close()
	if copyErr != nil || closeErr != nil || written != length {
		os.Remove(tmp.Name())
		return errors.New("chunk length mismatch")
	}
	if err := os.Rename(tmp.Name(), path); err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, `INSERT INTO encrypted_upload_chunks(upload_id,chunk_index,size_bytes,storage_path,nonce,tag) VALUES(?,?,?,?,?,?) ON CONFLICT(upload_id,chunk_index) DO UPDATE SET size_bytes=excluded.size_bytes,storage_path=excluded.storage_path,nonce=excluded.nonce,tag=excluded.tag`, upload, index, length, filepath.ToSlash(filepath.Join("blobs", "e2ee", vault, upload, fmt.Sprintf("%08d.chunk", index))), nonce, tag)
	return err
}
func (s *Service) Get(ctx context.Context, vault, upload, device string) (Session, error) {
	if err := s.open(ctx, vault, upload, device); err != nil {
		return Session{}, err
	}
	rows, err := s.db.QueryContext(ctx, "SELECT chunk_index FROM encrypted_upload_chunks WHERE upload_id=? ORDER BY chunk_index", upload)
	if err != nil {
		return Session{}, err
	}
	defer rows.Close()
	r := Session{ID: upload, ExpiresAt: time.Now().Add(s.ttl)}
	for rows.Next() {
		var i int
		rows.Scan(&i)
		r.Received = append(r.Received, i)
	}
	return r, rows.Err()
}
func (s *Service) Cancel(ctx context.Context, vault, upload, device string) error {
	if err := s.open(ctx, vault, upload, device); err != nil {
		return err
	}
	_, err := s.db.ExecContext(ctx, "DELETE FROM encrypted_uploads WHERE id=?", upload)
	if err == nil {
		os.RemoveAll(filepath.Join(s.root, "blobs", "e2ee", vault, upload))
	}
	return err
}

// Complete atomically promotes independently stored ciphertext chunks to an immutable object.
func (s *Service) Complete(ctx context.Context, vault, upload, device string, input CompleteInput) error {
	if input.ObjectID == "" || len(input.WrappedFileKey) == 0 || len(input.Manifest) == 0 || len(input.BaseNonce) != 12 || input.CiphertextHash == "" || input.ChunkCount <= 0 || input.AlgorithmVersion != 1 {
		return errors.New("invalid encrypted object metadata")
	}
	if err := s.open(ctx, vault, upload, device); err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, "SELECT chunk_index,size_bytes,storage_path FROM encrypted_upload_chunks WHERE upload_id=? ORDER BY chunk_index", upload)
	if err != nil {
		return err
	}
	defer rows.Close()
	type chunk struct {
		i    int
		size int64
		path string
	}
	chunks := []chunk{}
	var total int64
	for rows.Next() {
		var c chunk
		if err := rows.Scan(&c.i, &c.size, &c.path); err != nil {
			return err
		}
		if c.i != len(chunks) {
			return errors.New("missing or duplicate encrypted chunk")
		}
		chunks = append(chunks, c)
		total += c.size
	}
	if err := rows.Err(); err != nil {
		return err
	}
	if len(chunks) != input.ChunkCount {
		return errors.New("encrypted chunk count mismatch")
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO encrypted_objects(id,vault_id,ciphertext_hash,size_bytes,storage_path,chunk_size,chunk_count,base_nonce,encrypted_file_key,encrypted_manifest,algorithm_version,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)`, input.ObjectID, vault, input.CiphertextHash, total, "", ChunkSize, input.ChunkCount, input.BaseNonce, input.WrappedFileKey, input.Manifest, input.AlgorithmVersion, time.Now().UnixMilli()); err != nil {
		return fmt.Errorf("create encrypted object: %w", err)
	}
	for _, c := range chunks {
		if _, err = tx.ExecContext(ctx, "INSERT INTO encrypted_object_chunks(object_id,chunk_index,size_bytes,storage_path) VALUES(?,?,?,?)", input.ObjectID, c.i, c.size, c.path); err != nil {
			return err
		}
	}
	if _, err = tx.ExecContext(ctx, "UPDATE encrypted_uploads SET state='complete' WHERE id=? AND state='open'", upload); err != nil {
		return err
	}
	return tx.Commit()
}
func (s *Service) member(ctx context.Context, vault, device string) error {
	var n int
	err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM vault_members JOIN vaults ON vaults.id=vault_members.vault_id WHERE vault_id=? AND device_id=? AND vault_type='encrypted'", vault, device).Scan(&n)
	if err != nil {
		return err
	}
	if n != 1 {
		return ErrForbidden
	}
	return nil
}
func (s *Service) open(ctx context.Context, vault, upload, device string) error {
	if err := s.member(ctx, vault, device); err != nil {
		return err
	}
	var state string
	err := s.db.QueryRowContext(ctx, "SELECT state FROM encrypted_uploads WHERE id=? AND vault_id=? AND device_id=?", upload, vault, device).Scan(&state)
	if err == sql.ErrNoRows {
		return ErrNotFound
	}
	if err != nil {
		return err
	}
	if state != "open" {
		return ErrComplete
	}
	return nil
}
func CiphertextHash(r io.Reader) (string, error) {
	h := sha256.New()
	_, err := io.Copy(h, r)
	return hex.EncodeToString(h.Sum(nil)), err
}

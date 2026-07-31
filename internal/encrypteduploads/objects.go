package encrypteduploads

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

var ErrObjectNotFound = errors.New("encrypted object not found")

type Object struct {
	ID, VaultID, Hash string
	Size              int64
	ChunkCount        int
	CreatedAt         time.Time
}
type Chunk struct {
	Index int
	Size  int64
	Path  string
}

func (s *Service) GetObject(ctx context.Context, vault, id, device string) (Object, error) {
	if err := s.member(ctx, vault, device); err != nil {
		return Object{}, err
	}
	var o Object
	var created int64
	err := s.db.QueryRowContext(ctx, "SELECT id,vault_id,ciphertext_hash,size_bytes,chunk_count,created_at FROM encrypted_objects WHERE id=? AND vault_id=?", id, vault).Scan(&o.ID, &o.VaultID, &o.Hash, &o.Size, &o.ChunkCount, &created)
	if errors.Is(err, sql.ErrNoRows) {
		return Object{}, ErrObjectNotFound
	}
	if err != nil {
		return Object{}, fmt.Errorf("get encrypted object: %w", err)
	}
	o.CreatedAt = time.UnixMilli(created).UTC()
	return o, nil
}
func (s *Service) GetChunks(ctx context.Context, objectID string) ([]Chunk, error) {
	rows, err := s.db.QueryContext(ctx, "SELECT chunk_index,size_bytes,storage_path FROM encrypted_object_chunks WHERE object_id=? ORDER BY chunk_index", objectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []Chunk
	for rows.Next() {
		var c Chunk
		if err := rows.Scan(&c.Index, &c.Size, &c.Path); err != nil {
			return nil, err
		}
		if c.Index != len(result) {
			return nil, errors.New("corrupt encrypted chunk ordering")
		}
		result = append(result, c)
	}
	return result, rows.Err()
}

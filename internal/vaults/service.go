// Package vaults manages legacy and encrypted vault identities.
package vaults

import (
	"context"
	"crypto/rand"
	"database/sql"
	"fmt"
	"github.com/google/uuid"
	"photovault/e2ee"
	"time"
)

type Vault struct {
	ID, Type         string
	Salt             []byte
	Params           e2ee.KDFParams
	AlgorithmVersion int
}
type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db} }
func (r *Repository) Create(ctx context.Context, deviceID, vaultType string) (Vault, error) {
	if vaultType != "legacy" && vaultType != "encrypted" {
		return Vault{}, fmt.Errorf("invalid vault type")
	}
	v := Vault{ID: uuid.NewString(), Type: vaultType}
	if vaultType == "encrypted" {
		v.Salt = make([]byte, 16)
		if _, err := rand.Read(v.Salt); err != nil {
			return Vault{}, fmt.Errorf("generate vault salt: %w", err)
		}
		v.Params = e2ee.DefaultKDFParams()
		v.AlgorithmVersion = e2ee.AlgorithmVersion
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return Vault{}, err
	}
	defer tx.Rollback()
	_, err = tx.ExecContext(ctx, "INSERT INTO vaults(id,vault_type,salt,argon2_time,argon2_memory_kib,argon2_threads,algorithm_version,created_at) VALUES(?,?,?,?,?,?,?,?)", v.ID, v.Type, v.Salt, v.Params.Time, v.Params.Memory, v.Params.Threads, v.AlgorithmVersion, time.Now().UnixMilli())
	if err != nil {
		return Vault{}, fmt.Errorf("create vault: %w", err)
	}
	if _, err = tx.ExecContext(ctx, "INSERT INTO vault_members(vault_id,device_id,created_at) VALUES(?,?,?)", v.ID, deviceID, time.Now().UnixMilli()); err != nil {
		return Vault{}, fmt.Errorf("add vault member: %w", err)
	}
	if err = tx.Commit(); err != nil {
		return Vault{}, err
	}
	return v, nil
}

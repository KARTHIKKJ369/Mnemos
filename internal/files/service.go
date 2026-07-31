package files

import (
	"context"
	"fmt"
)

// ExistenceRepository provides indexed hash-existence lookups.
type ExistenceRepository interface {
	FindExistence(ctx context.Context, hash string) (Existence, bool, error)
}

// Service coordinates hash-existence lookup caching and persistence.
type Service struct {
	repository ExistenceRepository
	cache      ExistenceCache
}

// NewService constructs a file service with a repository and optional cache.
func NewService(repository ExistenceRepository, cache ExistenceCache) *Service {
	if cache == nil {
		cache = NoopExistenceCache{}
	}
	return &Service{repository: repository, cache: cache}
}

// Exists returns the permitted metadata for an existing content hash.
func (service *Service) Exists(ctx context.Context, hash string) (Existence, bool, error) {
	if existence, ok := service.cache.Get(hash); ok {
		return existence, true, nil
	}
	existence, found, err := service.repository.FindExistence(ctx, hash)
	if err != nil {
		return Existence{}, false, fmt.Errorf("find hash existence: %w", err)
	}
	if found {
		service.cache.Set(hash, existence)
	}
	return existence, found, nil
}

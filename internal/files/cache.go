package files

import (
	"container/list"
	"sync"
	"time"
)

// ExistenceCache stores positive hash-existence results.
type ExistenceCache interface {
	Get(hash string) (Existence, bool)
	Set(hash string, existence Existence)
}

// LRUExistenceCache is a concurrent in-memory LRU cache with per-entry expiration.
type LRUExistenceCache struct {
	mu       sync.Mutex
	capacity int
	ttl      time.Duration
	now      func() time.Time
	entries  map[string]*list.Element
	lru      *list.List
}

type cacheEntry struct {
	hash      string
	existence Existence
	expiresAt time.Time
}

// NewLRUExistenceCache constructs an LRU cache with the supplied capacity and TTL.
func NewLRUExistenceCache(capacity int, ttl time.Duration) *LRUExistenceCache {
	return &LRUExistenceCache{capacity: capacity, ttl: ttl, now: time.Now, entries: make(map[string]*list.Element), lru: list.New()}
}

// Get returns a non-expired cache entry and promotes it to most-recently-used.
func (cache *LRUExistenceCache) Get(hash string) (Existence, bool) {
	cache.mu.Lock()
	defer cache.mu.Unlock()
	element, ok := cache.entries[hash]
	if !ok {
		return Existence{}, false
	}
	entry := element.Value.(cacheEntry)
	if !cache.now().Before(entry.expiresAt) {
		cache.lru.Remove(element)
		delete(cache.entries, hash)
		return Existence{}, false
	}
	cache.lru.MoveToFront(element)
	return entry.existence, true
}

// Set stores an existence result when caching is enabled.
func (cache *LRUExistenceCache) Set(hash string, existence Existence) {
	if cache.capacity <= 0 || cache.ttl <= 0 {
		return
	}
	cache.mu.Lock()
	defer cache.mu.Unlock()
	entry := cacheEntry{hash: hash, existence: existence, expiresAt: cache.now().Add(cache.ttl)}
	if element, ok := cache.entries[hash]; ok {
		element.Value = entry
		cache.lru.MoveToFront(element)
		return
	}
	cache.entries[hash] = cache.lru.PushFront(entry)
	if cache.lru.Len() > cache.capacity {
		oldest := cache.lru.Back()
		oldestEntry := oldest.Value.(cacheEntry)
		delete(cache.entries, oldestEntry.hash)
		cache.lru.Remove(oldest)
	}
}

// NoopExistenceCache disables existence-result caching.
type NoopExistenceCache struct{}

// Get always reports a cache miss.
func (NoopExistenceCache) Get(string) (Existence, bool) { return Existence{}, false }

// Set intentionally discards cache values.
func (NoopExistenceCache) Set(string, Existence) {}

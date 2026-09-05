// Package ratelimit provides in-memory request rate limiters.
package ratelimit

import (
	"sync"
	"time"
)

// RegistrationLimiter limits device registrations per client IP address.
type RegistrationLimiter struct {
	mu      sync.Mutex
	entries map[string]entry
	now     func() time.Time
}

type entry struct {
	windowStartedAt time.Time
	count           int
}

const (
	registrationLimit  = 30
	registrationWindow = time.Minute
)

// NewRegistrationLimiter constructs a limiter allowing five registrations per minute per IP.
func NewRegistrationLimiter() *RegistrationLimiter {
	return &RegistrationLimiter{entries: make(map[string]entry), now: time.Now}
}

// Allow reports whether an IP may perform another registration in the current window.
func (limiter *RegistrationLimiter) Allow(remoteIP string) bool {
	limiter.mu.Lock()
	defer limiter.mu.Unlock()

	now := limiter.now()
	current := limiter.entries[remoteIP]
	if current.windowStartedAt.IsZero() || now.Sub(current.windowStartedAt) >= registrationWindow {
		limiter.entries[remoteIP] = entry{windowStartedAt: now, count: 1}
		return true
	}
	if current.count >= registrationLimit {
		return false
	}
	current.count++
	limiter.entries[remoteIP] = current
	return true
}

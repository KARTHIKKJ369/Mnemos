package ratelimit

import (
	"testing"
	"time"
)

func TestRegistrationLimiterAllowsFiveRequestsPerMinute(t *testing.T) {
	limiter := NewRegistrationLimiter()
	now := time.Date(2026, time.July, 31, 0, 0, 0, 0, time.UTC)
	limiter.now = func() time.Time { return now }

	for request := 0; request < registrationLimit; request++ {
		if !limiter.Allow("100.64.0.1") {
			t.Fatalf("request %d was unexpectedly rejected", request+1)
		}
	}
	if limiter.Allow("100.64.0.1") {
		t.Fatal("limit+1 request was allowed")
	}
	if !limiter.Allow("100.64.0.2") {
		t.Fatal("different IP was rejected")
	}
	now = now.Add(registrationWindow)
	if !limiter.Allow("100.64.0.1") {
		t.Fatal("request was rejected after rate-limit window elapsed")
	}
}

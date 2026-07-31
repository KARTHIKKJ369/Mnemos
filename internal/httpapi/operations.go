package httpapi

import (
	"context"
	"net/http"
	"photovault/internal/httperror"
	"photovault/internal/observability"
	"time"
)

type OperationsHandler struct {
	health  observability.Health
	metrics *observability.Metrics
}

func NewOperationsHandler(health observability.Health, metrics *observability.Metrics) *OperationsHandler {
	return &OperationsHandler{health, metrics}
}
func (h *OperationsHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) { http.NotFound(w, r) }
func (h *OperationsHandler) Metrics(w http.ResponseWriter, r *http.Request) {
	h.metrics.Handler().ServeHTTP(w, r)
}
func (h *OperationsHandler) Health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	body, err := h.health.Check(ctx)
	if err != nil {
		writeJSON(w, http.StatusServiceUnavailable, body)
		return
	}
	writeJSON(w, http.StatusOK, body)
}
func (h *OperationsHandler) Ready(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := h.health.Ready(ctx); err != nil {
		httperror.Write(w, http.StatusServiceUnavailable, "not_ready", "service dependencies are unavailable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ready"})
}

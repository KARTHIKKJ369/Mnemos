// Package observability exposes bounded-cardinality Prometheus instrumentation and operational checks.
package observability

import (
	"net/http"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

type Metrics struct {
	registry *prometheus.Registry
	requests *prometheus.CounterVec
	latency  *prometheus.HistogramVec
	errors   *prometheus.CounterVec
}

func NewMetrics() *Metrics {
	r := prometheus.NewRegistry()
	m := &Metrics{registry: r, requests: prometheus.NewCounterVec(prometheus.CounterOpts{Name: "photovault_http_requests_total", Help: "Completed HTTP requests."}, []string{"method", "route", "status"}), latency: prometheus.NewHistogramVec(prometheus.HistogramOpts{Name: "photovault_http_request_duration_seconds", Help: "HTTP request duration."}, []string{"method", "route"}), errors: prometheus.NewCounterVec(prometheus.CounterOpts{Name: "photovault_http_errors_total", Help: "HTTP errors."}, []string{"method", "route", "status"})}
	r.MustRegister(m.requests, m.latency, m.errors, prometheus.NewGoCollector(), prometheus.NewProcessCollector(prometheus.ProcessCollectorOpts{}))
	return m
}
func (m *Metrics) Handler() http.Handler {
	return promhttp.HandlerFor(m.registry, promhttp.HandlerOpts{})
}
func (m *Metrics) Observe(method, route string, status int, duration time.Duration) {
	m.requests.WithLabelValues(method, route, strconv.Itoa(status)).Inc()
	m.latency.WithLabelValues(method, route).Observe(duration.Seconds())
	if status >= 400 {
		m.errors.WithLabelValues(method, route, strconv.Itoa(status)).Inc()
	}
}

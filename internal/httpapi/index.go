package httpapi

import (
	"context"
	"errors"
	"github.com/go-chi/chi/v5"
	"net/http"
	"photovault/internal/authn"
	"photovault/internal/httperror"
	"photovault/internal/mediaindex"
	"strconv"
	"strings"
	"time"
)

type IndexService interface {
	Search(context.Context, mediaindex.Search) ([]mediaindex.Media, error)
	Get(context.Context, string) (mediaindex.Media, error)
	SetFavorite(context.Context, string, bool) error
	SoftDelete(context.Context, string) error
}
type IndexHandler struct{ service IndexService }

func NewIndexHandler(service IndexService) *IndexHandler                 { return &IndexHandler{service} }
func (h *IndexHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) { http.NotFound(w, r) }
func (h *IndexHandler) Search(w http.ResponseWriter, r *http.Request) {
	if _, ok := authn.DeviceFromContext(r.Context()); !ok {
		httperror.Write(w, 401, "unauthorized", "authentication failed")
		return
	}
	search, err := parseSearch(r)
	if err != nil {
		httperror.Write(w, 400, "invalid_request", err.Error())
		return
	}
	result, err := h.service.Search(r.Context(), search)
	if err != nil {
		httperror.Write(w, 500, "internal_error", "an internal error occurred")
		return
	}
	writeJSON(w, 200, map[string]any{"media": result, "limit": search.Limit, "offset": search.Offset})
}
func (h *IndexHandler) Get(w http.ResponseWriter, r *http.Request) {
	if _, ok := authn.DeviceFromContext(r.Context()); !ok {
		httperror.Write(w, 401, "unauthorized", "authentication failed")
		return
	}
	m, err := h.service.Get(r.Context(), chi.URLParam(r, "id"))
	if errors.Is(err, mediaindex.ErrNotFound) {
		httperror.Write(w, 404, "not_found", "media not found")
		return
	}
	if err != nil {
		httperror.Write(w, 500, "internal_error", "an internal error occurred")
		return
	}
	writeJSON(w, 200, m)
}
func (h *IndexHandler) Favorite(w http.ResponseWriter, r *http.Request) {
	if _, ok := authn.DeviceFromContext(r.Context()); !ok {
		httperror.Write(w, 401, "unauthorized", "authentication failed")
		return
	}
	value := r.Method == http.MethodPost
	err := h.service.SetFavorite(r.Context(), chi.URLParam(r, "id"), value)
	h.writeMutation(w, err)
}
func (h *IndexHandler) Delete(w http.ResponseWriter, r *http.Request) {
	if _, ok := authn.DeviceFromContext(r.Context()); !ok {
		httperror.Write(w, 401, "unauthorized", "authentication failed")
		return
	}
	h.writeMutation(w, h.service.SoftDelete(r.Context(), chi.URLParam(r, "id")))
}
func (h *IndexHandler) writeMutation(w http.ResponseWriter, err error) {
	if errors.Is(err, mediaindex.ErrNotFound) {
		httperror.Write(w, 404, "not_found", "media not found")
		return
	}
	if err != nil {
		httperror.Write(w, 500, "internal_error", "an internal error occurred")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
func parseSearch(r *http.Request) (mediaindex.Search, error) {
	q := r.URL.Query()
	s := mediaindex.Search{Query: strings.TrimSpace(q.Get("query")), MIMEType: strings.TrimSpace(q.Get("mime_type")), Sort: q.Get("sort"), Order: q.Get("order"), Limit: 100}
	var err error
	if q.Get("limit") != "" {
		s.Limit, err = strconv.Atoi(q.Get("limit"))
		if err != nil || s.Limit < 1 || s.Limit > 1000 {
			return s, errors.New("limit must be between 1 and 1000")
		}
	}
	if q.Get("offset") != "" {
		s.Offset, err = strconv.Atoi(q.Get("offset"))
		if err != nil || s.Offset < 0 {
			return s, errors.New("offset must be non-negative")
		}
	}
	for _, field := range []struct {
		key string
		out **time.Time
	}{{"from", &s.From}, {"to", &s.To}} {
		if v := q.Get(field.key); v != "" {
			t, e := time.Parse("2006-01-02", v)
			if e != nil {
				return s, errors.New(field.key + " must be YYYY-MM-DD")
			}
			*field.out = &t
		}
	}
	for _, field := range []struct {
		key string
		out **bool
	}{{"favorite", &s.Favorite}, {"has_thumbnail", &s.HasThumbnail}, {"has_preview", &s.HasPreview}} {
		if v := q.Get(field.key); v != "" {
			b, e := strconv.ParseBool(v)
			if e != nil {
				return s, errors.New(field.key + " must be boolean")
			}
			*field.out = &b
		}
	}
	return s, nil
}

package mediaindex

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"path/filepath"
	"photovault/internal/files"
	"strings"
	"time"
)

var ErrNotFound = errors.New("media index entry not found")

type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db} }
func (r *Repository) Enqueue(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "INSERT INTO media_index_jobs(file_id,state,next_attempt_at) VALUES (?, 'pending', ?) ON CONFLICT(file_id) DO NOTHING", id, time.Now().UnixMilli())
	return err
}
func (r *Repository) Upsert(ctx context.Context, f files.File, width, height *int) error {
	filename := f.OriginalFilename
	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(filename)), ".")
	_, err := r.db.ExecContext(ctx, `INSERT INTO media_index(file_id,filename,filename_normalized,extension,mime_type,size_bytes,width,height,uploaded_at,thumbnail_available,preview_available) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(file_id) DO UPDATE SET filename=excluded.filename,filename_normalized=excluded.filename_normalized,extension=excluded.extension,mime_type=excluded.mime_type,size_bytes=excluded.size_bytes,width=excluded.width,height=excluded.height,uploaded_at=excluded.uploaded_at,thumbnail_available=excluded.thumbnail_available,preview_available=excluded.preview_available`, f.ID, filename, strings.ToLower(filename), ext, f.MIMEType, f.SizeBytes, width, height, f.UploadedAt.UnixMilli(), boolInt(f.ThumbnailPath != ""), boolInt(f.PreviewPath != ""))
	return err
}
func (r *Repository) Search(ctx context.Context, s Search) ([]Media, error) {
	if s.Limit <= 0 {
		s.Limit = 100
	}
	if s.Limit > 1000 {
		s.Limit = 1000
	}
	sort := "uploaded_at"
	switch s.Sort {
	case "filename", "taken_at", "mime_type", "uploaded_at":
		sort = s.Sort
	}
	order := "DESC"
	if strings.EqualFold(s.Order, "asc") {
		order = "ASC"
	}
	where := []string{"deleted=0"}
	args := []any{}
	if s.Query != "" {
		where = append(where, "filename_normalized LIKE ?")
		args = append(args, "%"+strings.ToLower(s.Query)+"%")
	}
	if s.MIMEType != "" {
		where = append(where, "mime_type=?")
		args = append(args, s.MIMEType)
	}
	if s.From != nil {
		where = append(where, "taken_at>=?")
		args = append(args, s.From.UnixMilli())
	}
	if s.To != nil {
		where = append(where, "taken_at<=?")
		args = append(args, s.To.UnixMilli())
	}
	for _, filter := range []struct {
		v *bool
		c string
	}{{s.Favorite, "favorite"}, {s.HasThumbnail, "thumbnail_available"}, {s.HasPreview, "preview_available"}} {
		if filter.v != nil {
			where = append(where, filter.c+"=?")
			args = append(args, boolInt(*filter.v))
		}
	}
	args = append(args, s.Limit, s.Offset)
	query := fmt.Sprintf(`SELECT file_id,filename,extension,mime_type,size_bytes,width,height,duration_ms,taken_at,uploaded_at,camera_make,camera_model,gps_lat,gps_lon,favorite,deleted,thumbnail_available,preview_available FROM media_index WHERE %s ORDER BY %s %s, file_id ASC LIMIT ? OFFSET ?`, strings.Join(where, " AND "), sort, order)
	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("search media index: %w", err)
	}
	defer rows.Close()
	result := []Media{}
	for rows.Next() {
		m, err := scan(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, m)
	}
	return result, rows.Err()
}
func (r *Repository) Get(ctx context.Context, id string) (Media, error) {
	row := r.db.QueryRowContext(ctx, `SELECT file_id,filename,extension,mime_type,size_bytes,width,height,duration_ms,taken_at,uploaded_at,camera_make,camera_model,gps_lat,gps_lon,favorite,deleted,thumbnail_available,preview_available FROM media_index WHERE file_id=? AND deleted=0`, id)
	m, err := scan(row)
	if errors.Is(err, sql.ErrNoRows) {
		return Media{}, ErrNotFound
	}
	return m, err
}
func (r *Repository) SetFavorite(ctx context.Context, id string, value bool) error {
	return r.update(ctx, id, "favorite", boolInt(value))
}
func (r *Repository) SoftDelete(ctx context.Context, id string) error {
	return r.update(ctx, id, "deleted", 1)
}
func (r *Repository) update(ctx context.Context, id, column string, value any) error {
	result, err := r.db.ExecContext(ctx, "UPDATE media_index SET "+column+"=? WHERE file_id=?", value, id)
	if err != nil {
		return err
	}
	n, _ := result.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

type scanner interface{ Scan(...any) error }

func scan(row scanner) (Media, error) {
	var m Media
	var taken, uploaded sql.NullInt64
	var width, height sql.NullInt64
	var duration sql.NullInt64
	var make, model sql.NullString
	var lat, lon sql.NullFloat64
	var favorite, deleted, thumb, preview int
	err := row.Scan(&m.FileID, &m.Filename, &m.Extension, &m.MIMEType, &m.SizeBytes, &width, &height, &duration, &taken, &uploaded, &make, &model, &lat, &lon, &favorite, &deleted, &thumb, &preview)
	if err != nil {
		return Media{}, err
	}
	if width.Valid {
		v := int(width.Int64)
		m.Width = &v
	}
	if height.Valid {
		v := int(height.Int64)
		m.Height = &v
	}
	if duration.Valid {
		v := duration.Int64
		m.DurationMS = &v
	}
	if taken.Valid {
		v := time.UnixMilli(taken.Int64).UTC()
		m.TakenAt = &v
	}
	m.UploadedAt = time.UnixMilli(uploaded.Int64).UTC()
	if make.Valid {
		v := make.String
		m.CameraMake = &v
	}
	if model.Valid {
		v := model.String
		m.CameraModel = &v
	}
	if lat.Valid {
		v := lat.Float64
		m.GPSLat = &v
	}
	if lon.Valid {
		v := lon.Float64
		m.GPSLon = &v
	}
	m.Favorite = favorite != 0
	m.Deleted = deleted != 0
	m.ThumbnailAvailable = thumb != 0
	m.PreviewAvailable = preview != 0
	return m, nil
}
func boolInt(v bool) int {
	if v {
		return 1
	}
	return 0
}

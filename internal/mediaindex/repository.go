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

func (r *Repository) EnqueueMissingMetadata(ctx context.Context) (int64, error) {
	res, err := r.db.ExecContext(ctx, "INSERT INTO media_index_jobs(file_id,state,next_attempt_at) SELECT file_id, 'pending', ? FROM media_index WHERE taken_at IS NULL ON CONFLICT(file_id) DO NOTHING", time.Now().UnixMilli())
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}

func (r *Repository) Upsert(ctx context.Context, f files.File, meta ExtractedMetadata) error {
	filename := f.OriginalFilename
	ext := strings.TrimPrefix(strings.ToLower(filepath.Ext(filename)), ".")
	var takenAtMillis *int64
	if meta.TakenAt != nil {
		ms := meta.TakenAt.UnixMilli()
		takenAtMillis = &ms
	}
	_, err := r.db.ExecContext(ctx, `INSERT INTO media_index(
		file_id, filename, filename_normalized, extension, mime_type, size_bytes,
		width, height, duration_ms, taken_at, uploaded_at, camera_make, camera_model,
		gps_lat, gps_lon, thumbnail_available, preview_available
	) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(file_id) DO UPDATE SET
		filename=excluded.filename,
		filename_normalized=excluded.filename_normalized,
		extension=excluded.extension,
		mime_type=excluded.mime_type,
		size_bytes=excluded.size_bytes,
		width=COALESCE(excluded.width, media_index.width),
		height=COALESCE(excluded.height, media_index.height),
		duration_ms=COALESCE(excluded.duration_ms, media_index.duration_ms),
		taken_at=COALESCE(excluded.taken_at, media_index.taken_at),
		uploaded_at=excluded.uploaded_at,
		camera_make=COALESCE(excluded.camera_make, media_index.camera_make),
		camera_model=COALESCE(excluded.camera_model, media_index.camera_model),
		gps_lat=COALESCE(excluded.gps_lat, media_index.gps_lat),
		gps_lon=COALESCE(excluded.gps_lon, media_index.gps_lon),
		thumbnail_available=excluded.thumbnail_available,
		preview_available=excluded.preview_available`,
		f.ID, filename, strings.ToLower(filename), ext, f.MIMEType, f.SizeBytes,
		meta.Width, meta.Height, meta.DurationMS, takenAtMillis, f.UploadedAt.UnixMilli(),
		meta.CameraMake, meta.CameraModel, meta.GPSLat, meta.GPSLon,
		boolInt(f.ThumbnailPath != ""), boolInt(f.PreviewPath != ""),
	)
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
	case "filename", "taken_at", "mime_type", "uploaded_at", "size_bytes", "camera_make", "location":
		sort = s.Sort
	}
	order := "DESC"
	if strings.EqualFold(s.Order, "asc") {
		order = "ASC"
	}
	var where []string
	var args []any
	if s.Deleted != nil {
		where = append(where, "m.deleted=?")
		args = append(args, boolInt(*s.Deleted))
	} else {
		where = append(where, "m.deleted=0")
	}
	if s.Query != "" {
		where = append(where, "m.filename_normalized LIKE ?")
		args = append(args, "%"+strings.ToLower(s.Query)+"%")
	}
	if s.MIMEType != "" {
		if strings.HasSuffix(s.MIMEType, "/") {
			where = append(where, "m.mime_type LIKE ?")
			args = append(args, s.MIMEType+"%")
		} else {
			where = append(where, "m.mime_type=?")
			args = append(args, s.MIMEType)
		}
	}
	if s.From != nil {
		where = append(where, "COALESCE(m.taken_at, m.uploaded_at)>=?")
		args = append(args, s.From.UnixMilli())
	}
	if s.To != nil {
		where = append(where, "COALESCE(m.taken_at, m.uploaded_at)<=?")
		args = append(args, s.To.UnixMilli())
	}
	for _, filter := range []struct {
		v *bool
		c string
	}{{s.Favorite, "m.favorite"}, {s.HasThumbnail, "m.thumbnail_available"}, {s.HasPreview, "m.preview_available"}} {
		if filter.v != nil {
			where = append(where, filter.c+"=?")
			args = append(args, boolInt(*filter.v))
		}
	}
	if s.DeviceID != "" {
		where = append(where, "f.uploaded_by_device_id=?")
		args = append(args, s.DeviceID)
	}
	if s.ExcludeDeviceID != "" {
		where = append(where, "f.uploaded_by_device_id!=?")
		args = append(args, s.ExcludeDeviceID)
	}
	args = append(args, s.Limit, s.Offset)
	sortExpr := "m." + sort
	if sort == "taken_at" {
		sortExpr = "COALESCE(m.taken_at, m.uploaded_at)"
	} else if sort == "location" {
		sortExpr = "CASE WHEN m.gps_lat IS NOT NULL THEN 1 ELSE 0 END DESC, m.gps_lat"
	} else if sort == "filename" {
		sortExpr = "m.filename_normalized"
	}
	query := fmt.Sprintf(`SELECT m.file_id, m.filename, m.extension, m.mime_type, m.size_bytes, m.width, m.height, m.duration_ms, m.taken_at, m.uploaded_at, m.camera_make, m.camera_model, m.gps_lat, m.gps_lon, m.favorite, m.deleted, m.thumbnail_available, m.preview_available, COALESCE(f.uploaded_by_device_id, ''), COALESCE(d.name, 'Unknown device'), COALESCE(d.device_type, 'web') FROM media_index m LEFT JOIN files f ON m.file_id = f.id LEFT JOIN devices d ON f.uploaded_by_device_id = d.id WHERE %s ORDER BY %s %s, m.file_id ASC LIMIT ? OFFSET ?`, strings.Join(where, " AND "), sortExpr, order)
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
	row := r.db.QueryRowContext(ctx, `SELECT m.file_id, m.filename, m.extension, m.mime_type, m.size_bytes, m.width, m.height, m.duration_ms, m.taken_at, m.uploaded_at, m.camera_make, m.camera_model, m.gps_lat, m.gps_lon, m.favorite, m.deleted, m.thumbnail_available, m.preview_available, COALESCE(f.uploaded_by_device_id, ''), COALESCE(d.name, 'Unknown device'), COALESCE(d.device_type, 'web') FROM media_index m LEFT JOIN files f ON m.file_id = f.id LEFT JOIN devices d ON f.uploaded_by_device_id = d.id WHERE m.file_id=?`, id)
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
func (r *Repository) Restore(ctx context.Context, id string) error {
	return r.update(ctx, id, "deleted", 0)
}
func (r *Repository) PermanentDelete(ctx context.Context, id string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, "DELETE FROM media_index WHERE file_id=?", id)
	if err != nil {
		return err
	}
	n, _ := result.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	_, _ = tx.ExecContext(ctx, "DELETE FROM files WHERE id=?", id)
	return tx.Commit()
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
	var devID, devName, devType sql.NullString
	err := row.Scan(&m.FileID, &m.Filename, &m.Extension, &m.MIMEType, &m.SizeBytes, &width, &height, &duration, &taken, &uploaded, &make, &model, &lat, &lon, &favorite, &deleted, &thumb, &preview, &devID, &devName, &devType)
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
	m.Favorite = favorite == 1
	m.Deleted = deleted == 1
	m.ThumbnailAvailable = thumb == 1
	m.PreviewAvailable = preview == 1
	m.UploadedByDeviceID = devID.String
	m.UploadedByDeviceName = devName.String
	m.UploadedByDeviceType = devType.String
	return m, nil
}
func boolInt(v bool) int {
	if v {
		return 1
	}
	return 0
}

// Package mediaindex maintains the queryable metadata projection for stored media.
package mediaindex

import "time"

type Media struct {
	FileID, Filename, Extension, MIMEType, Hash             string
	SizeBytes                                               int64
	Width, Height                                           *int
	DurationMS                                              *int64
	TakenAt                                                 *time.Time
	UploadedAt                                              time.Time
	CameraMake, CameraModel                                 *string
	GPSLat, GPSLon                                          *float64
	Favorite, Deleted, ThumbnailAvailable, PreviewAvailable bool
}
type Search struct {
	Query, MIMEType                    string
	From, To                           *time.Time
	Favorite, HasThumbnail, HasPreview *bool
	Limit, Offset                      int
	Sort, Order                        string
}

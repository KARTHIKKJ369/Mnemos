// ─── Backend-derived types (do NOT duplicate backend logic) ───────────────────

/** Device types supported by the backend */
export type DeviceType = 'ios' | 'android' | 'mac' | 'web'

/** Device registration response */
export interface DeviceRegistration {
  device_id: string
  auth_token: string
}

/** Full media item from GET /media or GET /media/{id} */
export interface Media {
  FileID: string
  Filename: string
  Extension: string
  MIMEType: string
  Hash: string
  SizeBytes: number
  Width: number | null
  Height: number | null
  DurationMS: number | null
  TakenAt: string | null      // ISO string from Go time.Time
  UploadedAt: string          // ISO string
  CameraMake: string | null
  CameraModel: string | null
  GPSLat: number | null
  GPSLon: number | null
  Favorite: boolean
  Deleted: boolean
  ThumbnailAvailable: boolean
  PreviewAvailable: boolean
  UploadedByDeviceID?: string
  UploadedByDeviceName?: string
  UploadedByDeviceType?: string
}

/** Hash existence response from GET /files/exists */
export interface ExistenceHit {
  exists: true
  file_id: string
  size_bytes: number
}
export interface ExistenceMiss {
  exists: false
}
export type ExistenceResult = ExistenceHit | ExistenceMiss

/** File from sync diff */
export interface SyncFile {
  file_id: string
  hash: string
  filename: string
  mime_type: string
  size_bytes: number
  thumbnail_available: boolean
  preview_available: boolean
  uploaded_at: number   // unix ms
}

/** Sync diff response */
export interface SyncDiff {
  files: SyncFile[]
  next_since?: number
}

/** Sync ack response */
export interface SyncAck {
  acknowledged: number
}

/** Search query params */
export interface MediaSearchParams {
  query?: string
  mime_type?: string
  from?: string         // YYYY-MM-DD
  to?: string           // YYYY-MM-DD
  favorite?: boolean
  deleted?: boolean
  has_thumbnail?: boolean
  has_preview?: boolean
  device_id?: string
  exclude_device_id?: string
  limit?: number
  offset?: number
  sort?: 'filename' | 'taken_at' | 'mime_type' | 'uploaded_at' | 'size_bytes' | 'location'
  order?: 'asc' | 'desc'
}

/** Search response */
export interface MediaSearchResponse {
  media: Media[]
  limit: number
  offset: number
}

/** Registered device summary from GET /devices */
export interface DeviceSummary {
  id: string
  name: string
  device_type: DeviceType
  created_at: string
  last_seen_at: string
}

/** Server health and storage status */
export interface ServerHealth {
  version: string
  build_commit: string
  uptime_seconds: number
  database: string
  blob_storage: string
  workers: string
  disk_free_bytes?: number
  disk_total_bytes?: number
  storage_path?: string
  total_media?: number
  total_photos?: number
  total_videos?: number
  vault_bytes?: number
  total_devices?: number
}

/** Upload response */
export interface UploadResponse {
  file_id: string
  hash: string
  filename: string
  mime_type: string
  size_bytes: number
  status: 'uploading' | 'processing' | 'ready' | 'failed'
}

/** API error shape */
export interface APIError {
  code: string
  message: string
}

// ─── Client-only types ────────────────────────────────────────────────────────

/** Upload queue item */
export type UploadStatus = 'hashing' | 'checking' | 'uploading' | 'complete' | 'duplicate' | 'error' | 'cancelled'

export interface UploadItem {
  id: string
  file: File
  status: UploadStatus
  progress: number       // 0-100
  error?: string
  hash?: string
  fileId?: string        // returned after successful upload
}

/** Grouped media for timeline view */
export interface MediaGroup {
  label: string          // e.g. "July 2026"
  date: Date
  items: Media[]
}

/** View mode for gallery */
export type GalleryViewMode = 'grid' | 'timeline'

/** Auth state stored locally */
export interface AuthSession {
  deviceId: string
  authToken: string
  deviceName: string
  serverUrl: string
  isAdmin?: boolean
}

/** Sync state */
export interface SyncState {
  status: 'idle' | 'syncing' | 'error'
  lastSyncAt: number | null
  nextSince: number | null
  pendingCount: number
  error: string | null
}

export interface AuthBootstrapResponse {
  is_admin: boolean
  device_id?: string
  auth_token?: string
  device_name?: string
  device_type?: DeviceType
  network_allowed?: boolean
}

export interface ScanResult {
  scanned: number
  imported: number
  already_indexed: number
  errors: number
}

export interface ScanStatus {
  running: boolean
  last_path: string
  last_started?: string
  last_result?: ScanResult
  last_error?: string
}


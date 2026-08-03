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
  has_thumbnail?: boolean
  has_preview?: boolean
  limit?: number
  offset?: number
  sort?: 'filename' | 'taken_at' | 'mime_type' | 'uploaded_at'
  order?: 'asc' | 'desc'
}

/** Search response */
export interface MediaSearchResponse {
  media: Media[]
  limit: number
  offset: number
}

/** Vault type */
export type VaultType = 'legacy' | 'encrypted'

/** Vault create response */
export interface VaultCreateResponse {
  vault_id: string
  type: VaultType
  salt?: string        // base64, only for encrypted
  argon2?: {
    time: number
    memory_kib: number
    threads: number
  }
  algorithm_version?: number
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
}

/** Sync state */
export interface SyncState {
  status: 'idle' | 'syncing' | 'error'
  lastSyncAt: number | null
  nextSince: number | null
  pendingCount: number
  error: string | null
}


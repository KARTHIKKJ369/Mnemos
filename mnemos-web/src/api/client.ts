import type {
  DeviceRegistration,
  DeviceSummary,
  DeviceType,
  ExistenceResult,
  Media,
  MediaSearchParams,
  MediaSearchResponse,
  ServerHealth,
  SyncDiff,
  SyncAck,
  AuthBootstrapResponse,
  ScanStatus,
  UploadResponse,
} from '@/types'

// ─── Base fetcher ─────────────────────────────────────────────────────────────

export class APIClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'APIClientError'
  }
}

export function getBaseURL(): string {
  // If Vite dev server is running (e.g. port 5173), ALWAYS use relative '/api' proxy.
  // This guarantees that any client device on LAN (e.g. 192.168.1.6:5173) or Tailscale
  // automatically proxies all API requests, uploads, and media through the Vite dev server.
  if (typeof window !== 'undefined' && window.location.port === '5173') {
    return '/api'
  }

  try {
    const raw = localStorage.getItem('mnemos_session')
    if (raw) {
      const parsed = JSON.parse(raw)
      const serverUrl = parsed?.state?.session?.serverUrl ?? parsed?.session?.serverUrl ?? parsed?.serverUrl
      if (serverUrl && typeof serverUrl === 'string' && serverUrl.trim() !== '') {
        const trimmed = serverUrl.trim().replace(/\/+$/, '')
        const isLoopback = trimmed.includes('127.0.0.1') || trimmed.includes('localhost')
        const isRemote =
          typeof window !== 'undefined' &&
          window.location.hostname !== 'localhost' &&
          window.location.hostname !== '127.0.0.1'

        if (!(isLoopback && isRemote)) {
          return trimmed
        }
      }
    }
  } catch {
    // ignore parse error
  }
  return (import.meta.env.VITE_API_URL as string | undefined) ?? ''
}

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('mnemos_session')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return (
      parsed?.state?.session?.authToken ??
      parsed?.session?.authToken ??
      parsed?.authToken ??
      null
    )
  } catch {
    return null
  }
}

interface FetchOptions extends RequestInit {
  token?: string | null
}

export async function apiFetch<T>(path: string, options: FetchOptions = {}): Promise<T> {
  const { token: explicitToken, ...fetchOptions } = options
  const token = explicitToken !== undefined ? explicitToken : getToken()

  const headers = new Headers(fetchOptions.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (!headers.has('Content-Type') && !(fetchOptions.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${getBaseURL()}${path}`, {
    ...fetchOptions,
    headers,
  })

  if (!response.ok) {
    let code = 'unknown_error'
    let message = `HTTP ${response.status}`
    try {
      const body = (await response.json()) as {
        error?: { code?: string; message?: string }
        code?: string
        message?: string
      }
      code = body.error?.code ?? body.code ?? code
      message = body.error?.message ?? body.message ?? message
    } catch {
      // ignore parse failure
    }
    throw new APIClientError(response.status, code, message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

// ─── Auth / Device Registration ───────────────────────────────────────────────

export async function registerDevice(
  name: string,
  deviceType: DeviceType,
  token?: string,
): Promise<DeviceRegistration> {
  return apiFetch<DeviceRegistration>('/devices/register', {
    method: 'POST',
    body: JSON.stringify({ name, device_type: deviceType }),
    token: token ?? null,
  })
}

// ─── Health ───────────────────────────────────────────────────────────────────

export async function getHealth(): Promise<ServerHealth> {
  return apiFetch<ServerHealth>('/health', { token: null })
}

// ─── File existence (dedup) ───────────────────────────────────────────────────

export async function checkFileExists(hash: string): Promise<ExistenceResult> {
  return apiFetch<ExistenceResult>(`/files/exists?hash=${encodeURIComponent(hash)}`)
}

// ─── Upload ───────────────────────────────────────────────────────────────────

export async function uploadFile(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<UploadResponse> {
  return new Promise<UploadResponse>((resolve, reject) => {
    const token = getToken()
    if (!token) {
      reject(new APIClientError(401, 'unauthorized', 'No auth token'))
      return
    }

    const formData = new FormData()
    formData.append('file', file)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${getBaseURL()}/upload`)
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as UploadResponse)
        } catch {
          reject(new APIClientError(500, 'parse_error', 'Failed to parse upload response'))
        }
      } else {
        try {
          const body = JSON.parse(xhr.responseText) as {
            error?: { code?: string; message?: string }
            code?: string
            message?: string
          }
          reject(
            new APIClientError(
              xhr.status,
              body.error?.code ?? body.code ?? 'upload_error',
              body.error?.message ?? body.message ?? 'Upload failed',
            ),
          )
        } catch {
          reject(new APIClientError(xhr.status, 'upload_error', 'Upload failed'))
        }
      }
    }

    xhr.onerror = () => reject(new APIClientError(0, 'network_error', 'Network error during upload'))
    xhr.onabort = () => reject(new APIClientError(0, 'aborted', 'Upload aborted'))

    xhr.send(formData)
  })
}

// ─── Media ────────────────────────────────────────────────────────────────────

export async function searchMedia(params: MediaSearchParams = {}): Promise<MediaSearchResponse> {
  const query = new URLSearchParams()

  if (params.query) query.set('query', params.query)
  if (params.mime_type) query.set('mime_type', params.mime_type)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  if (params.favorite !== undefined) query.set('favorite', String(params.favorite))
  if (params.deleted !== undefined) query.set('deleted', String(params.deleted))
  if (params.has_thumbnail !== undefined) query.set('has_thumbnail', String(params.has_thumbnail))
  if (params.has_preview !== undefined) query.set('has_preview', String(params.has_preview))
  if (params.device_id) query.set('device_id', params.device_id)
  if (params.exclude_device_id) query.set('exclude_device_id', params.exclude_device_id)
  if (params.limit !== undefined) query.set('limit', String(params.limit))
  if (params.offset !== undefined) query.set('offset', String(params.offset))
  if (params.sort) query.set('sort', params.sort)
  if (params.order) query.set('order', params.order)

  const qs = query.toString()
  return apiFetch<MediaSearchResponse>(`/media${qs ? `?${qs}` : ''}`)
}

export async function getMedia(id: string): Promise<Media> {
  return apiFetch<Media>(`/media/${encodeURIComponent(id)}`)
}

export async function favoriteMedia(id: string): Promise<void> {
  return apiFetch<void>(`/media/${encodeURIComponent(id)}/favorite`, { method: 'POST' })
}

export async function unfavoriteMedia(id: string): Promise<void> {
  return apiFetch<void>(`/media/${encodeURIComponent(id)}/favorite`, { method: 'DELETE' })
}

export async function deleteMedia(id: string): Promise<void> {
  return apiFetch<void>(`/media/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function restoreMedia(id: string): Promise<void> {
  return apiFetch<void>(`/media/${encodeURIComponent(id)}/restore`, { method: 'POST' })
}

export async function permanentDeleteMedia(id: string): Promise<void> {
  return apiFetch<void>(`/media/${encodeURIComponent(id)}/permanent`, { method: 'DELETE' })
}

/** Build URL for media assets (used in <img> and <video> src) */
export function getMediaURL(id: string, type: 'original' | 'thumbnail' | 'preview'): string {
  const token = getToken()
  const base = `${getBaseURL()}/media/${encodeURIComponent(id)}/${type}`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

/** Build URL for downloading original media with Content-Disposition attachment */
export function getDownloadURL(id: string): string {
  const token = getToken()
  const base = `${getBaseURL()}/media/${encodeURIComponent(id)}/original?download=1`
  return token ? `${base}&token=${encodeURIComponent(token)}` : base
}

/** Trigger direct browser download of media */
export function downloadMedia(id: string, filename?: string) {
  const url = getDownloadURL(id)
  const a = document.createElement('a')
  a.href = url
  if (filename) a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

/** Fetch a media blob with auth (for use with object URLs in <img>) */
export async function fetchMediaBlob(id: string, type: 'original' | 'thumbnail' | 'preview'): Promise<string> {
  const token = getToken()
  const response = await fetch(`${getBaseURL()}/media/${encodeURIComponent(id)}/${type}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new APIClientError(response.status, 'fetch_error', 'Failed to fetch media')
  const blob = await response.blob()
  return URL.createObjectURL(blob)
}

// ─── Sync ─────────────────────────────────────────────────────────────────────

export async function getSyncDiff(since?: number, limit?: number): Promise<SyncDiff> {
  const query = new URLSearchParams()
  if (since !== undefined) query.set('since', String(since))
  if (limit !== undefined) query.set('limit', String(limit))
  const qs = query.toString()
  return apiFetch<SyncDiff>(`/sync/diff${qs ? `?${qs}` : ''}`)
}

export async function ackSync(fileIds: string[]): Promise<SyncAck> {
  return apiFetch<SyncAck>('/sync/ack', {
    method: 'POST',
    body: JSON.stringify({ file_ids: fileIds }),
  })
}

// ─── Devices ──────────────────────────────────────────────────────────────────

export async function getDevices(): Promise<{ devices: DeviceSummary[] }> {
  return apiFetch<{ devices: DeviceSummary[] }>('/devices')
}

export async function deleteDevice(id: string): Promise<void> {
  return apiFetch<void>(`/devices/${id}`, { method: 'DELETE' })
}

// ─── Bootstrap & Storage Scan ──────────────────────────────────────────────────

export async function getAuthBootstrap(serverUrl?: string): Promise<AuthBootstrapResponse> {
  const base = (serverUrl || getBaseURL()).replace(/\/+$/, '')
  const response = await fetch(`${base}/auth/bootstrap`, {
    headers: { 'Accept': 'application/json' },
  })
  if (!response.ok) {
    if (response.status === 403) {
      throw new APIClientError(403, 'network_forbidden', 'Access restricted to private Tailscale network')
    }
    throw new APIClientError(response.status, 'bootstrap_error', 'Failed to fetch bootstrap')
  }
  return response.json()
}

export async function scanStorageFolder(path?: string): Promise<{ message?: string; status?: ScanStatus; imported?: number }> {
  const base = getBaseURL()
  const token = getToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const response = await fetch(`${base}/storage/scan`, {
    method: 'POST',
    headers,
    body: JSON.stringify(path ? { path } : {}),
  })
  // 202 Accepted = scan started in background; 200 = legacy sync response
  if (response.status === 202 || response.status === 200) return response.json()
  let code = 'scan_error'
  let message = `HTTP ${response.status}`
  try {
    const body = (await response.json()) as { error?: { code?: string; message?: string } }
    code = body.error?.code ?? code
    message = body.error?.message ?? message
  } catch { /* ignore */ }
  throw new APIClientError(response.status, code, message)
}

export async function getScanStatus(): Promise<ScanStatus> {
  return apiFetch<ScanStatus>('/storage/scan/status')
}

export async function pickStorageFolder(): Promise<{ path?: string; cancelled: boolean }> {
  return apiFetch<{ path?: string; cancelled: boolean }>('/storage/pick-folder', {
    method: 'POST',
  })
}

export interface StorageConfig {
  storage_path: string
  env_path: string
  env_exists: boolean
}

export interface UpdateStorageConfigResponse {
  status: string
  storage_path: string
  requires_restart: boolean
  message: string
}

export async function getStorageConfig(): Promise<StorageConfig> {
  return apiFetch<StorageConfig>('/storage/config')
}

export async function updateStorageConfig(storagePath: string): Promise<UpdateStorageConfigResponse> {
  return apiFetch<UpdateStorageConfigResponse>('/storage/config', {
    method: 'POST',
    body: JSON.stringify({ storage_path: storagePath }),
  })
}




import type {
  DeviceRegistration,
  DeviceType,
  ExistenceResult,
  Media,
  MediaSearchParams,
  MediaSearchResponse,
  SyncDiff,
  SyncAck,
  UploadResponse,
  VaultCreateResponse,
  VaultType,
} from '@/types'
// Read session directly from Zustand store — no localStorage parsing, always correct
import { useAuthStore } from '@/stores/auth'

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
  const serverUrl = useAuthStore.getState().session?.serverUrl
  if (serverUrl) return serverUrl.replace(/\/+$/, '')
  return (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://127.0.0.1:8080'
}

export function getToken(): string | null {
  return useAuthStore.getState().session?.authToken ?? null
}

// ─── Core fetcher ─────────────────────────────────────────────────────────────

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

  const response = await fetch(`${getBaseURL()}${path}`, { ...fetchOptions, headers })

  if (!response.ok) {
    let code = 'unknown_error'
    let message = `HTTP ${response.status}`
    try {
      const body = (await response.json()) as { code?: string; message?: string }
      code = body.code ?? code
      message = body.message ?? message
    } catch { /* ignore */ }
    throw new APIClientError(response.status, code, message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export async function registerDevice(
  name: string,
  deviceType: DeviceType,
): Promise<DeviceRegistration> {
  return apiFetch<DeviceRegistration>('/devices/register', {
    method: 'POST',
    body: JSON.stringify({ name, device_type: deviceType }),
    token: null,
  })
}

// ─── Health ───────────────────────────────────────────────────────────────────

export async function getHealth(): Promise<{ status: string }> {
  return apiFetch<{ status: string }>('/health', { token: null })
}

// ─── File existence ───────────────────────────────────────────────────────────

export async function checkFileExists(hash: string): Promise<ExistenceResult> {
  return apiFetch<ExistenceResult>(`/files/exists?hash=${encodeURIComponent(hash)}`)
}

// ─── Upload ───────────────────────────────────────────────────────────────────

export async function uploadFile(
  file: File,
  storageId: string | undefined,
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
    if (storageId) formData.append('storage_id', storageId)

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
        try { resolve(JSON.parse(xhr.responseText) as UploadResponse) }
        catch { reject(new APIClientError(500, 'parse_error', 'Failed to parse upload response')) }
      } else {
        try {
          const body = JSON.parse(xhr.responseText) as { code?: string; message?: string }
          reject(new APIClientError(xhr.status, body.code ?? 'upload_error', body.message ?? 'Upload failed'))
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
  if (params.query)               query.set('query', params.query)
  if (params.mime_type)           query.set('mime_type', params.mime_type)
  if (params.from)                query.set('from', params.from)
  if (params.to)                  query.set('to', params.to)
  if (params.favorite !== undefined) query.set('favorite', String(params.favorite))
  if (params.has_thumbnail !== undefined) query.set('has_thumbnail', String(params.has_thumbnail))
  if (params.has_preview !== undefined)   query.set('has_preview', String(params.has_preview))
  if (params.limit !== undefined)  query.set('limit', String(params.limit))
  if (params.offset !== undefined) query.set('offset', String(params.offset))
  if (params.sort)                 query.set('sort', params.sort)
  if (params.order)                query.set('order', params.order)
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

export async function fetchMediaBlob(
  id: string,
  type: 'original' | 'thumbnail' | 'preview',
): Promise<string> {
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

// ─── Vaults ───────────────────────────────────────────────────────────────────

export async function createVault(type: VaultType): Promise<VaultCreateResponse> {
  return apiFetch<VaultCreateResponse>('/vaults', {
    method: 'POST',
    body: JSON.stringify({ type }),
  })
}

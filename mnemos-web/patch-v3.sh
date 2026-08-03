#!/usr/bin/env bash
# Mnemos FINAL combined patch — run once, replaces everything
# Usage: cd mnemos-web && bash ~/Downloads/patch-final.sh
set -e

write_file() { local p="$1"; mkdir -p "$(dirname "$p")"; cat > "$p"; }

echo '→ Applying Mnemos final patch...'

echo '  src/api/client.ts'
write_file "src/api/client.ts" << 'MEOF_8789fc09c7ef'
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

function getBaseURL(): string {
  return (import.meta.env.VITE_API_URL as string | undefined) ?? '/api'
}

function getToken(): string | null {
  try {
    const raw = localStorage.getItem('mnemos_session')
    if (!raw) return null
    const session = JSON.parse(raw) as { authToken?: string }
    return session.authToken ?? null
  } catch {
    return null
  }
}

interface FetchOptions extends RequestInit {
  token?: string | null
}

async function apiFetch<T>(path: string, options: FetchOptions = {}): Promise<T> {
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
      const body = (await response.json()) as { code?: string; message?: string }
      code = body.code ?? code
      message = body.message ?? message
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

export async function getHealth(): Promise<{ status: string }> {
  return apiFetch<{ status: string }>('/health', { token: null })
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

  if (params.query) query.set('query', params.query)
  if (params.mime_type) query.set('mime_type', params.mime_type)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  if (params.favorite !== undefined) query.set('favorite', String(params.favorite))
  if (params.has_thumbnail !== undefined) query.set('has_thumbnail', String(params.has_thumbnail))
  if (params.has_preview !== undefined) query.set('has_preview', String(params.has_preview))
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

/** Build URL for media assets (no fetch — used in <img> src) */
export function getMediaURL(id: string, type: 'original' | 'thumbnail' | 'preview'): string {
  const token = getToken()
  const base = `${getBaseURL()}/media/${encodeURIComponent(id)}/${type}`
  // We can't easily set headers on img tags, so we use query param auth fallback
  // The backend uses bearer token via header, so we need object URLs for <img>
  // For thumbnails, we'll use a token query approach or a proxy component
  return token ? `${base}?_t=${encodeURIComponent(token)}` : base
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

// ─── Vaults ───────────────────────────────────────────────────────────────────

export async function createVault(type: VaultType): Promise<VaultCreateResponse> {
  return apiFetch<VaultCreateResponse>('/vaults', {
    method: 'POST',
    body: JSON.stringify({ type }),
  })
}

MEOF_8789fc09c7ef

echo '  src/api/storage.ts'
write_file "src/api/storage.ts" << 'MEOF_098f58fa0d0f'
import type {
  StorageLibrary,
  SelectFolderResponse,
  VerifyStorageResponse,
  CreateStoragePayload,
  RenameStoragePayload,
} from '@/types/storage'
import { apiFetch } from './client'

// ─── Storage libraries ────────────────────────────────────────────────────────

/** List all configured storage libraries */
export async function getStorages(): Promise<StorageLibrary[]> {
  return apiFetch<StorageLibrary[]>('/storage')
}

/** Create a new storage library */
export async function createStorage(payload: CreateStoragePayload): Promise<StorageLibrary> {
  return apiFetch<StorageLibrary>('/storage', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Rename a storage library */
export async function renameStorage(id: string, payload: RenameStoragePayload): Promise<StorageLibrary> {
  return apiFetch<StorageLibrary>(`/storage/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

/** Set a storage library as the default upload destination */
export async function setDefaultStorage(id: string): Promise<void> {
  return apiFetch<void>(`/storage/${encodeURIComponent(id)}/default`, {
    method: 'POST',
  })
}

/** Verify a storage library's health */
export async function verifyStorage(id: string): Promise<VerifyStorageResponse> {
  return apiFetch<VerifyStorageResponse>(`/storage/${encodeURIComponent(id)}/verify`, {
    method: 'POST',
  })
}

/** Trigger a rescan of a storage library */
export async function rescanStorage(id: string): Promise<void> {
  return apiFetch<void>(`/storage/${encodeURIComponent(id)}/rescan`, {
    method: 'POST',
  })
}

/** Delete a storage library (does NOT delete files from disk) */
export async function deleteStorage(id: string): Promise<void> {
  return apiFetch<void>(`/storage/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}

// ─── Folder picker ────────────────────────────────────────────────────────────

/**
 * Ask the backend to open its native folder picker and return the selected path.
 * The frontend never browses the server filesystem directly.
 */
export async function selectFolder(): Promise<SelectFolderResponse> {
  return apiFetch<SelectFolderResponse>('/storage/select-folder', {
    method: 'POST',
  })
}
MEOF_098f58fa0d0f

echo '  src/lib/blobCache.ts'
write_file "src/lib/blobCache.ts" << 'MEOF_6cc3ee89e1e5'
/**
 * LRU Blob URL cache — manages object URL lifecycle across the app.
 *
 * Rules:
 * - Entries are keyed by `${mediaId}:${type}`
 * - Max 400 entries before LRU eviction (oldest evicted + URL revoked)
 * - Never revoke on component unmount — the cache owns the lifetime
 * - Map insertion order = LRU order (Map preserves insertion, we re-insert on access)
 */

class BlobURLCache {
  private readonly cache = new Map<string, string>()
  private readonly maxSize: number

  constructor(maxSize = 400) {
    this.maxSize = maxSize
  }

  get(key: string): string | undefined {
    const value = this.cache.get(key)
    if (value !== undefined) {
      // Promote to most-recently-used by re-inserting at tail
      this.cache.delete(key)
      this.cache.set(key, value)
    }
    return value
  }

  set(key: string, url: string): void {
    if (this.cache.has(key)) {
      // Update value, promote to tail
      this.cache.delete(key)
    } else if (this.cache.size >= this.maxSize) {
      // Evict LRU (first entry in Map)
      const firstKey = this.cache.keys().next().value
      if (firstKey !== undefined) {
        const evictedUrl = this.cache.get(firstKey)!
        URL.revokeObjectURL(evictedUrl)
        this.cache.delete(firstKey)
      }
    }
    this.cache.set(key, url)
  }

  has(key: string): boolean {
    return this.cache.has(key)
  }

  invalidate(key: string): void {
    const url = this.cache.get(key)
    if (url !== undefined) {
      URL.revokeObjectURL(url)
      this.cache.delete(key)
    }
  }

  invalidateMedia(mediaId: string): void {
    for (const type of ['thumbnail', 'preview', 'original'] as const) {
      this.invalidate(`${mediaId}:${type}`)
    }
  }

  clear(): void {
    this.cache.forEach((url) => URL.revokeObjectURL(url))
    this.cache.clear()
  }

  get size(): number {
    return this.cache.size
  }
}

/** Singleton shared across the entire app */
export const blobCache = new BlobURLCache(400)

export function makeBlobCacheKey(mediaId: string, type: 'thumbnail' | 'preview' | 'original'): string {
  return `${mediaId}:${type}`
}
MEOF_6cc3ee89e1e5

echo '  src/lib/hashWorker.ts'
write_file "src/lib/hashWorker.ts" << 'MEOF_a9a4fc123549'
/**
 * Off-main-thread SHA-256 hashing via an inline Web Worker.
 *
 * Why: file.arrayBuffer() + crypto.subtle.digest blocks the JS thread
 * for large files (videos). Running it in a Worker keeps the upload
 * queue UI and gallery rendering responsive.
 *
 * Implementation: worker code is embedded as a string and instantiated
 * via a Blob URL — no separate worker file needed, no bundler plugins.
 */

// ─── Worker source (runs in worker thread) ────────────────────────────────────

const WORKER_SRC = /* javascript */ `
self.onmessage = async function(e) {
  const { id, file } = e.data;
  try {
    const buffer = await file.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    const hex = Array.from(new Uint8Array(digest))
      .map(function(b) { return b.toString(16).padStart(2, '0'); })
      .join('');
    self.postMessage({ id, hash: hex });
  } catch (err) {
    self.postMessage({ id, error: err instanceof Error ? err.message : String(err) });
  }
};
`

// ─── Singleton worker ─────────────────────────────────────────────────────────

let _worker: Worker | null = null
let _workerBlobURL: string | null = null

function getWorker(): Worker {
  if (!_worker) {
    const blob = new Blob([WORKER_SRC], { type: 'text/javascript' })
    _workerBlobURL = URL.createObjectURL(blob)
    _worker = new Worker(_workerBlobURL)

    // If the worker crashes, tear it down so the next call recreates it
    _worker.onerror = () => {
      _worker = null
      if (_workerBlobURL) {
        URL.revokeObjectURL(_workerBlobURL)
        _workerBlobURL = null
      }
    }
  }
  return _worker
}

// ─── Public API ───────────────────────────────────────────────────────────────

let _idCounter = 0

/**
 * Hash a File using SHA-256 in a Web Worker.
 * Non-blocking — the main thread stays responsive during hashing.
 */
export function hashFileInWorker(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const id = String(++_idCounter)

    const worker = getWorker()

    const onMessage = (e: MessageEvent<{ id: string; hash?: string; error?: string }>) => {
      if (e.data.id !== id) return
      worker.removeEventListener('message', onMessage)
      if (e.data.error) {
        reject(new Error(e.data.error))
      } else {
        resolve(e.data.hash!)
      }
    }

    worker.addEventListener('message', onMessage)
    worker.postMessage({ id, file })
  })
}

/** Tear down the worker (called on logout / page unload) */
export function destroyHashWorker(): void {
  if (_worker) {
    _worker.terminate()
    _worker = null
  }
  if (_workerBlobURL) {
    URL.revokeObjectURL(_workerBlobURL)
    _workerBlobURL = null
  }
}
MEOF_a9a4fc123549

echo '  src/types/storage.ts'
write_file "src/types/storage.ts" << 'MEOF_d0bc36996447'
/** Storage library as returned by GET /storage */
export interface StorageLibrary {
  id: string
  name: string
  path: string
  free_space: number     // bytes
  healthy: boolean
  default: boolean
}

/** Response from POST /storage/select-folder */
export interface SelectFolderResponse {
  path: string
}

/** Response from POST /storage/verify */
export interface VerifyStorageResponse {
  healthy: boolean
  message?: string
}

/** Payload for creating a storage library */
export interface CreateStoragePayload {
  name: string
  path: string
}

/** Payload for renaming a storage library */
export interface RenameStoragePayload {
  name: string
}
MEOF_d0bc36996447

echo '  src/stores/upload.ts'
write_file "src/stores/upload.ts" << 'MEOF_4c3271c216bb'
import { create } from 'zustand'
import { v4 as uuidv4 } from 'uuid'
import type { UploadItem, UploadStatus } from '@/types'

interface UploadStore {
  queue: UploadItem[]
  isOpen: boolean
  addFiles: (files: File[]) => void
  updateItem: (id: string, patch: Partial<UploadItem>) => void
  removeItem: (id: string) => void
  clearCompleted: () => void
  setOpen: (open: boolean) => void
  activeCount: () => number
  pendingCount: () => number
}

const TERMINAL_STATUSES: UploadStatus[] = ['complete', 'duplicate', 'error', 'cancelled']

export const useUploadStore = create<UploadStore>()((set, get) => ({
  queue: [],
  isOpen: false,

  addFiles: (files) => {
    const items: UploadItem[] = files.map((file) => ({
      id: uuidv4(),
      file,
      status: 'hashing',
      progress: 0,
    }))
    set((s) => ({ queue: [...s.queue, ...items], isOpen: true }))
  },

  updateItem: (id, patch) => {
    set((s) => ({
      queue: s.queue.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    }))
  },

  removeItem: (id) => {
    set((s) => ({ queue: s.queue.filter((item) => item.id !== id) }))
  },

  clearCompleted: () => {
    set((s) => ({
      queue: s.queue.filter((item) => !TERMINAL_STATUSES.includes(item.status)),
    }))
  },

  setOpen: (open) => set({ isOpen: open }),

  activeCount: () => {
    const { queue } = get()
    return queue.filter((i) => !TERMINAL_STATUSES.includes(i.status)).length
  },

  pendingCount: () => {
    const { queue } = get()
    return queue.filter((i) => i.status === 'hashing' || i.status === 'checking').length
  },
}))

MEOF_4c3271c216bb

echo '  src/stores/storage.ts'
write_file "src/stores/storage.ts" << 'MEOF_387fab24364e'
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

interface StorageStore {
  /** ID of the library selected as the upload destination. null = use server default. */
  selectedLibraryId: string | null
  setSelectedLibraryId: (id: string | null) => void
}

export const useStorageStore = create<StorageStore>()(
  persist(
    (set) => ({
      selectedLibraryId: null,
      setSelectedLibraryId: (id) => set({ selectedLibraryId: id }),
    }),
    {
      name: 'mnemos_storage_prefs',
      storage: createJSONStorage(() => localStorage),
    },
  ),
)
MEOF_387fab24364e

echo '  src/hooks/useStorage.ts'
write_file "src/hooks/useStorage.ts" << 'MEOF_c47b308385ee'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getStorages, createStorage, renameStorage, setDefaultStorage,
  verifyStorage, rescanStorage, deleteStorage, selectFolder,
} from '@/api/storage'
import type { CreateStoragePayload, RenameStoragePayload } from '@/types/storage'
import { APIClientError } from '@/api/client'

export const storageKeys = {
  all: ['storage'] as const,
  list: () => [...storageKeys.all, 'list'] as const,
}

/** Returns 404/501 → not yet implemented on this backend */
function isNotImplemented(err: unknown): boolean {
  if (err instanceof APIClientError) {
    return err.status === 404 || err.status === 501 || err.status === 0
  }
  return false
}

export function useStorages() {
  return useQuery({
    queryKey: storageKeys.list(),
    queryFn: getStorages,
    staleTime: 30_000,
    retry: (count, err) => isNotImplemented(err) ? false : count < 2,
  })
}

/**
 * hasStorage: true if ≥1 library configured OR backend has no storage endpoint yet.
 * backendSupported: false means /storage returned 404/501 — skip wizard entirely.
 */
export function useHasStorage(): {
  hasStorage: boolean
  isLoading: boolean
  backendSupported: boolean
} {
  const { data, isLoading, isError, error } = useStorages()

  // Backend doesn't have storage yet — skip wizard, show gallery normally
  if (isError && isNotImplemented(error)) {
    return { hasStorage: true, isLoading: false, backendSupported: false }
  }

  return {
    hasStorage: (data?.length ?? 0) > 0,
    isLoading,
    backendSupported: !isError,
  }
}

export function useDefaultStorage() {
  const { data } = useStorages()
  return data?.find((s) => s.default) ?? data?.[0] ?? null
}

export function useCreateStorage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateStoragePayload) => createStorage(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: storageKeys.list() }),
  })
}

export function useRenameStorage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: RenameStoragePayload }) =>
      renameStorage(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: storageKeys.list() }),
  })
}

export function useSetDefaultStorage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => setDefaultStorage(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: storageKeys.list() }),
  })
}

export function useVerifyStorage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => verifyStorage(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: storageKeys.list() }),
  })
}

export function useRescanStorage() {
  return useMutation({ mutationFn: (id: string) => rescanStorage(id) })
}

export function useDeleteStorage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteStorage(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: storageKeys.list() }),
  })
}

export function useSelectFolder() {
  return useMutation({ mutationFn: selectFolder })
}
MEOF_c47b308385ee

echo '  src/hooks/useMedia.ts'
write_file "src/hooks/useMedia.ts" << 'MEOF_ae59626c4e33'
import {
  useQuery,
  useMutation,
  useQueryClient,
  useInfiniteQuery,
  type InfiniteData,
} from '@tanstack/react-query'
import {
  searchMedia,
  getMedia,
  favoriteMedia,
  unfavoriteMedia,
  deleteMedia,
  fetchMediaBlob,
} from '@/api/client'
import type { Media, MediaSearchParams, MediaSearchResponse } from '@/types'

// ─── Query keys ──────────────────────────────────────────────────────────────

export const mediaKeys = {
  all: ['media'] as const,
  lists: () => [...mediaKeys.all, 'list'] as const,
  list: (params: MediaSearchParams) => [...mediaKeys.lists(), params] as const,
  infinite: (params: MediaSearchParams) => [...mediaKeys.all, 'infinite', params] as const,
  detail: (id: string) => [...mediaKeys.all, 'detail', id] as const,
  blob: (id: string, type: string) => [...mediaKeys.all, 'blob', id, type] as const,
}

// ─── Hooks ───────────────────────────────────────────────────────────────────

const PAGE_SIZE = 100

export function useMediaSearch(params: MediaSearchParams) {
  return useQuery({
    queryKey: mediaKeys.list(params),
    queryFn: () => searchMedia({ ...params, limit: PAGE_SIZE }),
    staleTime: 30_000,
  })
}

export function useMediaInfinite(params: Omit<MediaSearchParams, 'offset' | 'limit'>) {
  return useInfiniteQuery<
    MediaSearchResponse,
    Error,
    InfiniteData<MediaSearchResponse>,
    ReturnType<typeof mediaKeys.infinite>,
    number
  >({
    queryKey: mediaKeys.infinite(params),
    queryFn: ({ pageParam }) =>
      searchMedia({ ...params, limit: PAGE_SIZE, offset: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last, all) => {
      const loaded = all.reduce((acc, p) => acc + p.media.length, 0)
      return last.media.length === PAGE_SIZE ? loaded : undefined
    },
    staleTime: 30_000,
  })
}

export function useMediaDetail(id: string | null) {
  return useQuery({
    queryKey: mediaKeys.detail(id ?? ''),
    queryFn: () => getMedia(id!),
    enabled: id !== null,
    staleTime: 60_000,
  })
}

export function useMediaBlob(id: string | null, type: 'thumbnail' | 'preview' | 'original', enabled = true) {
  return useQuery({
    queryKey: mediaKeys.blob(id ?? '', type),
    queryFn: () => fetchMediaBlob(id!, type),
    enabled: id !== null && enabled,
    staleTime: Infinity,
    gcTime: 5 * 60_000,
  })
}

export function useFavoriteMedia() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, favorite }: { id: string; favorite: boolean }) =>
      favorite ? favoriteMedia(id) : unfavoriteMedia(id),
    onMutate: async ({ id, favorite }) => {
      await queryClient.cancelQueries({ queryKey: mediaKeys.all })
      // Optimistic update on detail
      queryClient.setQueryData<Media>(mediaKeys.detail(id), (old) =>
        old ? { ...old, Favorite: favorite } : old,
      )
      // Optimistic update in lists
      queryClient.setQueriesData<InfiniteData<MediaSearchResponse>>(
        { queryKey: mediaKeys.lists() },
        (old) => {
          if (!old) return old
          return {
            ...old,
            pages: old.pages.map((page) => ({
              ...page,
              media: page.media.map((m) => (m.FileID === id ? { ...m, Favorite: favorite } : m)),
            })),
          }
        },
      )
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.all })
    },
  })
}

export function useDeleteMedia() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteMedia(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.lists() })
    },
  })
}

MEOF_ae59626c4e33

echo '  src/components/shared/AuthImage.tsx'
write_file "src/components/shared/AuthImage.tsx" << 'MEOF_2e8b55884ef4'
import { useState, useEffect, useRef } from 'react'
import { fetchMediaBlob } from '@/api/client'
import { cn } from '@/lib/utils'

interface AuthImageProps {
  mediaId: string
  type: 'thumbnail' | 'preview' | 'original'
  alt: string
  className?: string
  onLoad?: () => void
  placeholder?: React.ReactNode
}

/** Fetches media with the auth token and renders it as an <img>.
 *  Cleans up the object URL on unmount to prevent memory leaks. */
export function AuthImage({
  mediaId,
  type,
  alt,
  className,
  onLoad,
  placeholder,
}: AuthImageProps) {
  const [src, setSrc] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)
  const urlRef = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setSrc(null)
    setLoaded(false)
    setError(false)

    fetchMediaBlob(mediaId, type)
      .then((objectUrl) => {
        if (cancelled) {
          URL.revokeObjectURL(objectUrl)
          return
        }
        // Revoke old URL
        if (urlRef.current) URL.revokeObjectURL(urlRef.current)
        urlRef.current = objectUrl
        setSrc(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })

    return () => {
      cancelled = true
    }
  }, [mediaId, type])

  useEffect(() => {
    return () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current)
    }
  }, [])

  if (error) {
    return (
      <div className={cn('flex items-center justify-center bg-[--color-surface-overlay]', className)}>
        <span className="text-[--color-text-disabled] text-xs">Failed</span>
      </div>
    )
  }

  return (
    <>
      {(!src || !loaded) && (
        <div className={cn('skeleton', className)}>
          {placeholder}
        </div>
      )}
      {src && (
        <img
          src={src}
          alt={alt}
          className={cn(
            'transition-opacity duration-[220ms] ease-out',
            loaded ? 'opacity-100' : 'opacity-0 absolute',
            className,
          )}
          onLoad={() => {
            setLoaded(true)
            onLoad?.()
          }}
        />
      )}
    </>
  )
}

MEOF_2e8b55884ef4

echo '  src/components/ui/Button.tsx'
write_file "src/components/ui/Button.tsx" << 'MEOF_91e22d3f3765'
import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type Variant = 'default' | 'ghost' | 'destructive' | 'outline' | 'accent'
type Size = 'sm' | 'md' | 'lg' | 'icon'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

const variants: Record<Variant, string> = {
  default:
    'bg-[--color-surface-subtle] text-[--color-text-primary] hover:bg-[--color-surface-muted] ' +
    'border border-[--color-border-default]',
  accent:
    'bg-[--color-accent] text-[--color-surface-base] hover:bg-[--color-accent-dim] ' +
    'border border-transparent font-medium',
  ghost:
    'bg-transparent text-[--color-text-secondary] hover:bg-[--color-surface-overlay] ' +
    'hover:text-[--color-text-primary] border border-transparent',
  outline:
    'bg-transparent text-[--color-text-primary] border border-[--color-border-default] ' +
    'hover:bg-[--color-surface-overlay]',
  destructive:
    'bg-[--color-danger-surface] text-[--color-danger] hover:bg-red-900 ' +
    'border border-red-900',
}

const sizes: Record<Size, string> = {
  sm: 'h-7 px-3 text-xs gap-1.5 rounded-[--radius-md]',
  md: 'h-9 px-4 text-sm gap-2 rounded-[--radius-md]',
  lg: 'h-11 px-6 text-sm gap-2 rounded-[--radius-lg]',
  icon: 'h-9 w-9 rounded-[--radius-md] justify-center',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'default', size = 'md', loading, disabled, children, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled ?? loading}
        className={cn(
          // Base
          'inline-flex items-center cursor-pointer select-none',
          'font-medium transition-none',
          'focus-visible:outline-2 focus-visible:outline-[--color-accent] focus-visible:outline-offset-2',
          'disabled:opacity-40 disabled:cursor-not-allowed',
          // Apple-physics: instant response on pointer-down
          'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
          // Release spring: ease-out slightly longer
          'transition-transform duration-[150ms] ease-out',
          variants[variant],
          sizes[size],
          className,
        )}
        {...props}
      >
        {loading ? (
          <span className="animate-spin h-3.5 w-3.5 border-2 border-current border-t-transparent rounded-full" />
        ) : (
          children
        )}
      </button>
    )
  },
)

Button.displayName = 'Button'

MEOF_91e22d3f3765

echo '  src/components/ui/Input.tsx'
write_file "src/components/ui/Input.tsx" << 'MEOF_0065dfad8487'
import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  leftIcon?: React.ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, leftIcon, ...props }, ref) => {
    if (leftIcon) {
      return (
        <div className="relative flex items-center">
          <span className="absolute left-3 text-[--color-text-muted] pointer-events-none">
            {leftIcon}
          </span>
          <input
            ref={ref}
            className={cn(
              'w-full pl-9 pr-3 h-9 text-sm',
              'bg-[--color-surface-overlay] text-[--color-text-primary]',
              'border border-[--color-border-default] rounded-[--radius-md]',
              'placeholder:text-[--color-text-disabled]',
              'focus:outline-none focus:border-[--color-accent]',
              'transition-colors duration-[150ms]',
              className,
            )}
            {...props}
          />
        </div>
      )
    }

    return (
      <input
        ref={ref}
        className={cn(
          'w-full px-3 h-9 text-sm',
          'bg-[--color-surface-overlay] text-[--color-text-primary]',
          'border border-[--color-border-default] rounded-[--radius-md]',
          'placeholder:text-[--color-text-disabled]',
          'focus:outline-none focus:border-[--color-accent]',
          'transition-colors duration-[150ms]',
          className,
        )}
        {...props}
      />
    )
  },
)

Input.displayName = 'Input'

MEOF_0065dfad8487

echo '  src/components/ui/Toasts.tsx'
write_file "src/components/ui/Toasts.tsx" << 'MEOF_452c641eaf3c'
import { AnimatePresence, motion } from 'motion/react'
import { CheckCircle, XCircle, Info, X } from 'lucide-react'
import { useUIStore } from '@/stores/ui'

const icons = {
  success: CheckCircle,
  error: XCircle,
  info: Info,
}

const colors = {
  success: 'text-[--color-success]',
  error: 'text-[--color-danger]',
  info: 'text-[--color-text-secondary]',
}

export function Toasts() {
  const { toasts, removeToast } = useUIStore()

  return (
    <div
      className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 pointer-events-none"
      aria-live="polite"
    >
      <AnimatePresence mode="popLayout">
        {toasts.map((toast) => {
          const Icon = icons[toast.type]
          return (
            <motion.div
              key={toast.id}
              layout
              initial={{ opacity: 0, y: 12, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 4, scale: 0.98 }}
              transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
              className={[
                'pointer-events-auto flex items-center gap-3',
                'bg-[--color-surface-overlay] border border-[--color-border-default]',
                'rounded-[--radius-lg] shadow-[--shadow-3] px-4 py-3',
                'min-w-64 max-w-sm',
              ].join(' ')}
            >
              <Icon size={16} className={colors[toast.type]} />
              <p className="text-sm text-[--color-text-primary] flex-1">{toast.message}</p>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors ml-1"
                aria-label="Dismiss"
              >
                <X size={14} />
              </button>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}

MEOF_452c641eaf3c

echo '  src/features/auth/AuthPage.tsx'
write_file "src/features/auth/AuthPage.tsx" << 'MEOF_bc369b018e49'
import { useState } from 'react'
import { motion } from 'motion/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Images, Server, Smartphone } from 'lucide-react'
import { registerDevice, APIClientError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

const schema = z.object({
  serverUrl: z.string().url('Must be a valid URL'),
  deviceName: z.string().min(1, 'Required').max(100, 'Too long'),
  deviceType: z.enum(['ios', 'android', 'mac', 'web'] as const),
})

type FormData = z.infer<typeof schema>

export function AuthPage() {
  const { setSession } = useAuthStore()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      serverUrl: 'http://127.0.0.1:8080',
      deviceName: 'My Browser',
      deviceType: 'web',
    },
  })

  const onSubmit = async (data: FormData) => {
    setError(null)
    try {
      const result = await registerDevice(data.deviceName, data.deviceType)
      setSession({
        deviceId: result.device_id,
        authToken: result.auth_token,
        deviceName: data.deviceName,
        serverUrl: data.serverUrl,
      })
    } catch (err) {
      if (err instanceof APIClientError) {
        setError(err.message)
      } else {
        setError('Could not connect to the server. Check the URL and try again.')
      }
    }
  }

  return (
    <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.32 }}
        className="w-full max-w-sm"
      >
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-[--radius-lg] bg-[--color-accent] flex items-center justify-center">
            <Images size={18} className="text-[--color-surface-base]" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-[--color-text-primary] tracking-tight">Mnemos</h1>
            <p className="text-xs text-[--color-text-muted]">Self-hosted photo vault</p>
          </div>
        </div>

        <div className="space-y-1 mb-8">
          <h2 className="text-xl font-semibold text-[--color-text-primary] tracking-tight">Connect to server</h2>
          <p className="text-sm text-[--color-text-secondary]">Register this browser as a new device.</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Server URL</label>
            <Input {...register('serverUrl')} placeholder="http://100.x.x.x:8080" leftIcon={<Server size={13} />} autoComplete="url" />
            {errors.serverUrl && <p className="text-xs text-[--color-danger]">{errors.serverUrl.message}</p>}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Device name</label>
            <Input {...register('deviceName')} placeholder="My Browser" leftIcon={<Smartphone size={13} />} autoComplete="off" />
            {errors.deviceName && <p className="text-xs text-[--color-danger]">{errors.deviceName.message}</p>}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Device type</label>
            <select
              {...register('deviceType')}
              className="w-full px-3 h-9 text-sm bg-[--color-surface-overlay] text-[--color-text-primary] border border-[--color-border-default] rounded-[--radius-md] focus:outline-none focus:border-[--color-accent] transition-colors duration-[150ms]"
            >
              <option value="web">Web</option>
              <option value="mac">Mac</option>
              <option value="ios">iOS</option>
              <option value="android">Android</option>
            </select>
          </div>

          {error && (
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-xs text-[--color-danger] bg-[--color-danger-surface] px-3 py-2 rounded-[--radius-md]"
            >
              {error}
            </motion.p>
          )}

          <Button type="submit" variant="accent" size="lg" loading={isSubmitting} className="w-full mt-2">
            Register device
          </Button>
        </form>

        <p className="text-xs text-[--color-text-disabled] mt-8 text-center">
          The auth token is shown only once and stored locally.
        </p>
      </motion.div>
    </div>
  )
}

MEOF_bc369b018e49

echo '  src/features/upload/UploadQueue.tsx'
write_file "src/features/upload/UploadQueue.tsx" << 'MEOF_9583cfef710e'
import { useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { X, CheckCircle2, AlertCircle, RefreshCw, Upload } from 'lucide-react'
import { useUploadStore } from '@/stores/upload'
import { checkFileExists, uploadFile } from '@/api/client'
import { hashFile, formatBytes } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { UploadItem } from '@/types'

const MAX_CONCURRENT = 3

// ─── Upload engine (processes queue) ─────────────────────────────────────────

export function useUploadEngine() {
  const { queue, updateItem } = useUploadStore()
  const processingRef = useRef<Set<string>>(new Set())

  useEffect(() => {
    const pending = queue.filter(
      (i) => i.status === 'hashing' && !processingRef.current.has(i.id),
    )
    const slots = MAX_CONCURRENT - processingRef.current.size

    pending.slice(0, slots).forEach((item) => {
      processingRef.current.add(item.id)
      processItem(item, updateItem).finally(() => processingRef.current.delete(item.id))
    })
  })
}

async function processItem(
  item: UploadItem,
  updateItem: (id: string, patch: Partial<UploadItem>) => void,
) {
  try {
    // 1. Hash
    updateItem(item.id, { status: 'hashing' })
    const hash = await hashFile(item.file)
    updateItem(item.id, { hash })

    // 2. Check existence (dedup)
    updateItem(item.id, { status: 'checking' })
    const existence = await checkFileExists(hash)
    if (existence.exists) {
      updateItem(item.id, { status: 'duplicate', fileId: existence.file_id, progress: 100 })
      return
    }

    // 3. Upload
    updateItem(item.id, { status: 'uploading', progress: 0 })
    const result = await uploadFile(item.file, (progress) => {
      updateItem(item.id, { progress })
    })
    updateItem(item.id, { status: 'complete', fileId: result.file_id, progress: 100 })
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Upload failed'
    updateItem(item.id, { status: 'error', error: message })
  }
}

// ─── Single queue item UI ─────────────────────────────────────────────────────

function QueueItem({ item }: { item: UploadItem }) {
  const { removeItem, updateItem } = useUploadStore()

  const retry = () => updateItem(item.id, { status: 'hashing', progress: 0, error: undefined })

  const statusIcon = () => {
    switch (item.status) {
      case 'complete': return <CheckCircle2 size={14} className="text-[--color-success]" />
      case 'duplicate': return <CheckCircle2 size={14} className="text-[--color-text-muted]" />
      case 'error': return <AlertCircle size={14} className="text-[--color-danger]" />
      default: return null
    }
  }

  const statusLabel = () => {
    switch (item.status) {
      case 'hashing': return 'Hashing…'
      case 'checking': return 'Checking…'
      case 'uploading': return `${item.progress}%`
      case 'complete': return 'Done'
      case 'duplicate': return 'Already exists'
      case 'error': return item.error ?? 'Error'
      case 'cancelled': return 'Cancelled'
    }
  }

  const isDone = item.status === 'complete' || item.status === 'duplicate'

  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.2 }}
      className="overflow-hidden"
    >
      <div className="px-4 py-2.5 flex items-start gap-3">
        {/* File icon */}
        <div className={cn(
          'w-7 h-7 rounded-[--radius-sm] flex-shrink-0 flex items-center justify-center text-[10px] font-bold',
          'bg-[--color-surface-subtle] text-[--color-text-muted]',
        )}>
          {item.file.name.split('.').pop()?.toUpperCase().slice(0, 3) ?? 'FILE'}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <p className="text-xs text-[--color-text-primary] truncate">{item.file.name}</p>
          <div className="flex items-center gap-1.5 mt-0.5">
            {statusIcon()}
            <span className={cn(
              'text-xs',
              item.status === 'error' ? 'text-[--color-danger]' : 'text-[--color-text-muted]',
            )}>
              {statusLabel()}
            </span>
            <span className="text-[10px] text-[--color-text-disabled]">
              · {formatBytes(item.file.size)}
            </span>
          </div>

          {/* Progress bar */}
          {item.status === 'uploading' && (
            <div className="mt-1.5 h-0.5 bg-[--color-surface-subtle] rounded-full overflow-hidden">
              <motion.div
                className="h-full bg-[--color-accent] rounded-full"
                style={{ width: `${item.progress}%` }}
                transition={{ duration: 0.15 }}
              />
            </div>
          )}

          {/* Indeterminate bar for hashing/checking */}
          {(item.status === 'hashing' || item.status === 'checking') && (
            <div className="mt-1.5 h-0.5 bg-[--color-surface-subtle] rounded-full overflow-hidden">
              <motion.div
                className="h-full w-1/3 bg-[--color-text-disabled] rounded-full"
                animate={{ x: ['0%', '200%'] }}
                transition={{ repeat: Infinity, duration: 1, ease: 'linear' }}
              />
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1 flex-shrink-0">
          {item.status === 'error' && (
            <button
              onClick={retry}
              className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
              aria-label="Retry"
            >
              <RefreshCw size={12} />
            </button>
          )}
          {(isDone || item.status === 'error') && (
            <button
              onClick={() => removeItem(item.id)}
              className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
              aria-label="Remove"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>
    </motion.div>
  )
}

// ─── Drop zone overlay ────────────────────────────────────────────────────────

function DropZone() {
  const { addFiles } = useUploadStore()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFiles = (files: File[]) => {
    const media = files.filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
    if (media.length > 0) addFiles(media)
  }

  return (
    <div
      className={[
        'mx-4 border-2 border-dashed border-[--color-border-default] rounded-[--radius-lg]',
        'p-6 flex flex-col items-center gap-2 cursor-pointer',
        'hover:border-[--color-accent] hover:bg-[--color-surface-overlay] transition-colors',
        'active:scale-[0.98] active:transition-transform active:duration-[80ms]',
      ].join(' ')}
      onClick={() => fileInputRef.current?.click()}
    >
      <Upload size={20} className="text-[--color-text-muted]" />
      <p className="text-xs text-[--color-text-secondary] text-center">
        Click to select files<br />
        <span className="text-[--color-text-muted]">or drag & drop anywhere</span>
      </p>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,video/*"
        className="sr-only"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          handleFiles(files)
          e.target.value = ''
        }}
      />
    </div>
  )
}

// ─── Upload queue panel ───────────────────────────────────────────────────────

interface UploadQueueProps {
  onClose: () => void
}

export function UploadQueue({ onClose }: UploadQueueProps) {
  const { queue, clearCompleted } = useUploadStore()
  const completedCount = queue.filter(
    (i) => i.status === 'complete' || i.status === 'duplicate',
  ).length

  // Start the engine
  useUploadEngine()

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 h-11 border-b border-[--color-border-subtle] flex-shrink-0">
        <span className="text-sm font-medium text-[--color-text-primary]">Uploads</span>
        <div className="flex items-center gap-1">
          {completedCount > 0 && (
            <Button size="sm" variant="ghost" onClick={clearCompleted}>
              Clear
            </Button>
          )}
          <Button size="icon" variant="ghost" onClick={onClose}>
            <X size={14} />
          </Button>
        </div>
      </div>

      {/* Drop zone */}
      <div className="py-3">
        <DropZone />
      </div>

      {/* Queue */}
      {queue.length > 0 && (
        <>
          <div className="px-4 py-2">
            <p className="text-[10px] text-[--color-text-disabled] uppercase tracking-widest font-semibold">
              Queue — {queue.length} item{queue.length > 1 ? 's' : ''}
            </p>
          </div>
          <div className="flex-1 overflow-y-auto">
            <AnimatePresence mode="popLayout">
              {queue.map((item) => (
                <QueueItem key={item.id} item={item} />
              ))}
            </AnimatePresence>
          </div>
        </>
      )}
    </div>
  )
}

MEOF_9583cfef710e

echo '  src/features/gallery/GalleryPage.tsx'
write_file "src/features/gallery/GalleryPage.tsx" << 'MEOF_a6e69c8bd4d7'
import { useMemo, useCallback } from 'react'
import { Virtuoso } from 'react-virtuoso'
import { motion, AnimatePresence } from 'motion/react'
import { Images, Grid3X3, Clock, CheckSquare, X, Trash2, Heart } from 'lucide-react'
import { useMediaInfinite } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { useFavoriteMedia, useDeleteMedia } from '@/hooks/useMedia'
import { groupByMonth } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { PhotoTile } from './PhotoTile'
import type { Media, MediaGroup } from '@/types'

// ─── Toolbar ─────────────────────────────────────────────────────────────────

function GalleryToolbar({ groups }: { groups: MediaGroup[] }) {
  const { viewMode, setViewMode, isSelectMode, toggleSelectMode, selectedIds, clearSelection, selectAll, addToast } =
    useUIStore()
  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()
  const allIds = useMemo(() => groups.flatMap((g) => g.items.map((m) => m.FileID)), [groups])

  const handleBulkFavorite = async () => {
    const ids = Array.from(selectedIds)
    await Promise.allSettled(ids.map((id) => favoriteMedia.mutateAsync({ id, favorite: true })))
    addToast({ type: 'success', message: `Favorited ${ids.length} item${ids.length > 1 ? 's' : ''}` })
    clearSelection()
  }

  const handleBulkDelete = async () => {
    const ids = Array.from(selectedIds)
    await Promise.allSettled(ids.map((id) => deleteMedia.mutateAsync(id)))
    addToast({ type: 'success', message: `Deleted ${ids.length} item${ids.length > 1 ? 's' : ''}` })
    clearSelection()
  }

  return (
    <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
      <div className="flex items-center gap-2">
        <AnimatePresence mode="wait">
          {isSelectMode ? (
            <motion.div
              key="select"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.12 }}
              className="flex items-center gap-2"
            >
              <span className="text-sm text-[--color-text-secondary]">
                {selectedIds.size} selected
              </span>
              <Button size="sm" variant="ghost" onClick={() => selectAll(allIds)}>
                All
              </Button>
              {selectedIds.size > 0 && (
                <>
                  <Button size="sm" variant="ghost" onClick={handleBulkFavorite}>
                    <Heart size={13} />
                    Favorite
                  </Button>
                  <Button size="sm" variant="destructive" onClick={handleBulkDelete}>
                    <Trash2 size={13} />
                    Delete
                  </Button>
                </>
              )}
            </motion.div>
          ) : (
            <motion.h1
              key="title"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.12 }}
              className="text-sm font-semibold text-[--color-text-primary]"
            >
              Library
            </motion.h1>
          )}
        </AnimatePresence>
      </div>

      <div className="flex items-center gap-1">
        {isSelectMode ? (
          <Button size="sm" variant="ghost" onClick={clearSelection}>
            <X size={13} />
            Done
          </Button>
        ) : (
          <>
            <Button
              size="icon"
              variant="ghost"
              onClick={() => setViewMode('grid')}
              className={viewMode === 'grid' ? 'text-[--color-text-primary]' : ''}
              aria-label="Grid view"
            >
              <Grid3X3 size={15} />
            </Button>
            <Button
              size="icon"
              variant="ghost"
              onClick={() => setViewMode('timeline')}
              className={viewMode === 'timeline' ? 'text-[--color-text-primary]' : ''}
              aria-label="Timeline view"
            >
              <Clock size={15} />
            </Button>
            <div className="w-px h-4 bg-[--color-border-default] mx-1" />
            <Button size="sm" variant="ghost" onClick={toggleSelectMode}>
              <CheckSquare size={13} />
              Select
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

// ─── Month group header ───────────────────────────────────────────────────────

function GroupHeader({ label }: { label: string }) {
  return (
    <div className="px-6 pt-6 pb-2 sticky top-0 z-10 bg-[--color-surface-base]">
      <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">
        {label}
      </h2>
    </div>
  )
}

// ─── Photo grid row ───────────────────────────────────────────────────────────

const COLS = 5
const GAP = 3

function PhotoRow({ items }: { items: Media[] }) {
  return (
    <div
      className="grid px-6"
      style={{
        gridTemplateColumns: `repeat(${COLS}, 1fr)`,
        gap: `${GAP}px`,
      }}
    >
      {items.map((media) => (
        <PhotoTile key={media.FileID} media={media} />
      ))}
    </div>
  )
}

// ─── Row-ified data structure for Virtuoso ───────────────────────────────────

type VirtRow =
  | { type: 'header'; label: string }
  | { type: 'row'; items: Media[] }

function buildVirtRows(groups: MediaGroup[]): VirtRow[] {
  const rows: VirtRow[] = []
  for (const group of groups) {
    rows.push({ type: 'header', label: group.label })
    for (let i = 0; i < group.items.length; i += COLS) {
      rows.push({ type: 'row', items: group.items.slice(i, i + COLS) })
    }
  }
  return rows
}

// ─── Empty state ─────────────────────────────────────────────────────────────

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center h-full gap-4 text-center p-12">
      <div className="w-16 h-16 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
        <Images size={28} className="text-[--color-text-disabled]" />
      </div>
      <div className="space-y-1">
        <h3 className="text-sm font-medium text-[--color-text-primary]">No photos yet</h3>
        <p className="text-xs text-[--color-text-muted] max-w-48">
          Upload your first photo or sync from another device to get started.
        </p>
      </div>
    </div>
  )
}

// ─── Main gallery ─────────────────────────────────────────────────────────────

export function GalleryPage() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useMediaInfinite({
    sort: 'taken_at',
    order: 'desc',
  })

  const allMedia = useMemo(
    () => data?.pages.flatMap((p) => p.media) ?? [],
    [data],
  )

  const groups = useMemo(() => groupByMonth(allMedia), [allMedia])
  const virtRows = useMemo(() => buildVirtRows(groups), [groups])

  const endReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  if (isLoading) {
    return (
      <div className="flex flex-col h-full">
        <div className="h-11 border-b border-[--color-border-subtle]" />
        <div className="flex-1 p-6 grid gap-1" style={{ gridTemplateColumns: `repeat(${COLS}, 1fr)` }}>
          {Array.from({ length: 20 }).map((_, i) => (
            <div key={i} className="skeleton aspect-square rounded-sm" />
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <GalleryToolbar groups={groups} />

      {allMedia.length === 0 ? (
        <EmptyState />
      ) : (
        <Virtuoso
          className="flex-1 overflow-y-auto"
          totalCount={virtRows.length}
          endReached={endReached}
          overscan={800}
          itemContent={(index) => {
            const row = virtRows[index]
            if (!row) return null
            if (row.type === 'header') return <GroupHeader label={row.label} />
            return <PhotoRow items={row.items} />
          }}
          components={{
            Footer: isFetchingNextPage
              ? () => (
                  <div className="flex justify-center py-8">
                    <span className="animate-spin h-4 w-4 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
                  </div>
                )
              : undefined,
          }}
        />
      )}
    </div>
  )
}

MEOF_a6e69c8bd4d7

echo '  src/features/gallery/TimelinePage.tsx'
write_file "src/features/gallery/TimelinePage.tsx" << 'MEOF_11b5b528839a'
import { useMemo } from 'react'
import { Virtuoso } from 'react-virtuoso'
import { Clock } from 'lucide-react'
import { useMediaInfinite } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { groupByMonth } from '@/lib/utils'
import { AuthImage } from '@/components/shared/AuthImage'
import { MediaViewer } from '@/features/viewer/MediaViewer'
import type { Media } from '@/types'

type Row =
  | { type: 'header'; label: string; count: number }
  | { type: 'items'; items: Media[] }

const COLS = 6

function buildRows(groups: ReturnType<typeof groupByMonth>): Row[] {
  const rows: Row[] = []
  for (const g of groups) {
    rows.push({ type: 'header', label: g.label, count: g.items.length })
    for (let i = 0; i < g.items.length; i += COLS) {
      rows.push({ type: 'items', items: g.items.slice(i, i + COLS) })
    }
  }
  return rows
}

export function TimelinePage() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } =
    useMediaInfinite({ sort: 'taken_at', order: 'desc' })
  const { openViewer } = useUIStore()

  const allMedia = useMemo(
    () => data?.pages.flatMap((p) => p.media) ?? [],
    [data],
  )
  const allIds = useMemo(() => allMedia.map((m) => m.FileID), [allMedia])
  const groups = useMemo(() => groupByMonth(allMedia), [allMedia])
  const rows = useMemo(() => buildRows(groups), [groups])

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Clock size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Timeline</h1>
        {allMedia.length > 0 && (
          <span className="text-xs text-[--color-text-muted]">· {allMedia.length.toLocaleString()} items</span>
        )}
      </div>

      {isLoading ? (
        <div className="flex-1 p-6 space-y-6">
          {[1, 2].map((i) => (
            <div key={i}>
              <div className="h-4 w-32 skeleton rounded mb-3" />
              <div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${COLS}, 1fr)` }}>
                {Array.from({ length: COLS * 2 }).map((_, j) => (
                  <div key={j} className="aspect-square skeleton rounded-sm" />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Virtuoso
          className="flex-1"
          totalCount={rows.length}
          overscan={1000}
          endReached={() => {
            if (hasNextPage && !isFetchingNextPage) fetchNextPage()
          }}
          itemContent={(index) => {
            const row = rows[index]
            if (!row) return null
            if (row.type === 'header') {
              return (
                <div className="px-6 pt-6 pb-2 sticky top-0 z-10 bg-[--color-surface-base]">
                  <div className="flex items-baseline gap-2">
                    <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">
                      {row.label}
                    </h2>
                    <span className="text-[10px] text-[--color-text-disabled]">{row.count}</span>
                  </div>
                </div>
              )
            }
            return (
              <div
                className="grid px-6 mb-0.5"
                style={{ gridTemplateColumns: `repeat(${COLS}, 1fr)`, gap: '2px' }}
              >
                {row.items.map((media) => (
                  <button
                    key={media.FileID}
                    className="aspect-square overflow-hidden relative group cursor-pointer active:scale-[0.97] active:transition-transform active:duration-[80ms]"
                    onClick={() => openViewer(media.FileID)}
                  >
                    {media.ThumbnailAvailable ? (
                      <AuthImage
                        mediaId={media.FileID}
                        type="thumbnail"
                        alt={media.Filename}
                        className="absolute inset-0 w-full h-full object-cover"
                      />
                    ) : (
                      <div className="absolute inset-0 skeleton" />
                    )}
                    <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 photo-overlay" />
                  </button>
                ))}
              </div>
            )
          }}
          components={{
            Footer: isFetchingNextPage
              ? () => (
                  <div className="flex justify-center py-6">
                    <span className="animate-spin h-4 w-4 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
                  </div>
                )
              : undefined,
          }}
        />
      )}

      <MediaViewer allIds={allIds} />
    </div>
  )
}

MEOF_11b5b528839a

echo '  src/features/gallery/PhotoTile.tsx'
write_file "src/features/gallery/PhotoTile.tsx" << 'MEOF_81c517fcb5d1'
import { useState, useCallback } from 'react'
import { motion } from 'motion/react'
import { Heart, Play, CheckCircle2 } from 'lucide-react'
import { AuthImage } from '@/components/shared/AuthImage'
import { useUIStore } from '@/stores/ui'
import { useFavoriteMedia } from '@/hooks/useMedia'
import { isVideo } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { Media } from '@/types'

interface PhotoTileProps {
  media: Media
}

export function PhotoTile({ media }: PhotoTileProps) {
  const { openViewer, isSelectMode, selectedIds, toggleSelect, addToast } = useUIStore()
  const favoriteMedia = useFavoriteMedia()
  const [isHovered, setIsHovered] = useState(false)

  const isSelected = selectedIds.has(media.FileID)
  const video = isVideo(media.MIMEType)

  const handleClick = useCallback(() => {
    if (isSelectMode) {
      toggleSelect(media.FileID)
      return
    }
    openViewer(media.FileID)
  }, [isSelectMode, media.FileID, toggleSelect, openViewer])

  const handleFavorite = useCallback(
    async (e: React.MouseEvent) => {
      e.stopPropagation()
      await favoriteMedia.mutateAsync({ id: media.FileID, favorite: !media.Favorite })
      addToast({
        type: 'success',
        message: media.Favorite ? 'Removed from favorites' : 'Added to favorites',
      })
    },
    [media.FileID, media.Favorite, favoriteMedia, addToast],
  )

  return (
    <motion.div
      className={cn(
        'relative aspect-square overflow-hidden rounded-sm cursor-pointer',
        'select-none group',
        isSelected && 'ring-2 ring-[--color-accent] ring-inset',
      )}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      whileTap={{ scale: 0.97 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.12 }}
    >
      {/* Thumbnail */}
      {media.ThumbnailAvailable ? (
        <AuthImage
          mediaId={media.FileID}
          type="thumbnail"
          alt={media.Filename}
          className="absolute inset-0 w-full h-full object-cover"
        />
      ) : (
        <div className="absolute inset-0 skeleton" />
      )}

      {/* Overlay on hover */}
      <div
        className={cn(
          'absolute inset-0 bg-black/30 photo-overlay',
          isHovered || isSelected ? 'opacity-100' : 'opacity-0',
        )}
      />

      {/* Video indicator */}
      {video && (
        <div className="absolute bottom-1.5 right-1.5 bg-black/60 rounded-full p-1">
          <Play size={10} className="text-white fill-white" />
        </div>
      )}

      {/* Favorite badge */}
      {media.Favorite && !isHovered && !isSelectMode && (
        <div className="absolute top-1.5 right-1.5">
          <Heart size={12} className="text-white fill-white drop-shadow" />
        </div>
      )}

      {/* Hover controls */}
      {(isHovered || isSelectMode) && !isSelected && (
        <motion.button
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.1 }}
          onClick={handleFavorite}
          className={cn(
            'absolute top-1.5 right-1.5 p-1 rounded-full',
            'bg-black/40 hover:bg-black/60 transition-colors',
            'text-white',
          )}
          aria-label={media.Favorite ? 'Remove from favorites' : 'Add to favorites'}
        >
          <Heart
            size={12}
            className={cn(media.Favorite ? 'fill-white' : 'fill-transparent')}
          />
        </motion.button>
      )}

      {/* Selection checkbox */}
      {(isSelectMode || isHovered) && (
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ type: 'spring', bounce: 0, duration: 0.15 }}
          className="absolute top-1.5 left-1.5"
        >
          {isSelected ? (
            <CheckCircle2 size={18} className="text-white fill-[--color-accent] drop-shadow" />
          ) : (
            <div className="w-4.5 h-4.5 rounded-full border-2 border-white/70 bg-black/20" />
          )}
        </motion.div>
      )}
    </motion.div>
  )
}

MEOF_81c517fcb5d1

echo '  src/features/viewer/MediaViewer.tsx'
write_file "src/features/viewer/MediaViewer.tsx" << 'MEOF_f6ff93a7dfda'
import { useEffect, useCallback, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  X, ChevronLeft, ChevronRight, Heart, Trash2, 
  Info, 
} from 'lucide-react'
import { useMediaDetail, useFavoriteMedia, useDeleteMedia, useMediaBlob } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { formatBytes, formatDate, isVideo } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { Media } from '@/types'

// ─── Metadata panel ───────────────────────────────────────────────────────────

function MetadataRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <span className="text-xs text-[--color-text-muted] w-20 flex-shrink-0 pt-0.5">{label}</span>
      <span className="text-xs text-[--color-text-secondary] leading-relaxed">{value}</span>
    </div>
  )
}

function MetadataPanel({ media }: { media: Media }) {
  return (
    <div className="w-64 flex-shrink-0 border-l border-[--color-border-subtle] bg-[--color-surface-raised] p-5 overflow-y-auto">
      <h3 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest mb-4">
        Info
      </h3>
      <div className="space-y-3">
        <MetadataRow label="Filename" value={media.Filename} />
        {media.TakenAt && (
          <MetadataRow
            label="Taken"
            value={formatDate(new Date(media.TakenAt))}
          />
        )}
        <MetadataRow label="Size" value={formatBytes(media.SizeBytes)} />
        <MetadataRow label="Type" value={media.MIMEType} />
        {media.Width && media.Height && (
          <MetadataRow label="Dimensions" value={`${media.Width} × ${media.Height}`} />
        )}
        {media.DurationMS && (
          <MetadataRow
            label="Duration"
            value={`${Math.floor(media.DurationMS / 1000)}s`}
          />
        )}
        {(media.CameraMake ?? media.CameraModel) && (
          <MetadataRow
            label="Camera"
            value={[media.CameraMake, media.CameraModel].filter(Boolean).join(' ')}
          />
        )}
        {media.GPSLat !== null && media.GPSLon !== null && (
          <MetadataRow
            label="Location"
            value={`${media.GPSLat.toFixed(4)}, ${media.GPSLon.toFixed(4)}`}
          />
        )}
        <MetadataRow label="Hash" value={media.Hash.slice(0, 16) + '…'} />
      </div>
    </div>
  )
}

// ─── Viewer image ─────────────────────────────────────────────────────────────

function ViewerImage({ media }: { media: Media }) {
  const { data: src, isLoading } = useMediaBlob(
    media.FileID,
    media.PreviewAvailable ? 'preview' : 'original',
  )

  const video = isVideo(media.MIMEType)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center w-full h-full">
        <span className="animate-spin h-6 w-6 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!src) return null

  if (video) {
    return (
      <video
        src={src}
        controls
        autoPlay
        className="max-w-full max-h-full rounded-[--radius-md] object-contain"
        style={{ maxWidth: 'calc(100vw - 96px)', maxHeight: 'calc(100vh - 80px)' }}
      />
    )
  }

  return (
    <motion.img
      key={src}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.18 }}
      src={src}
      alt={media.Filename}
      className="max-w-full max-h-full object-contain rounded-sm select-none"
      style={{ maxWidth: 'calc(100vw - 64px)', maxHeight: 'calc(100vh - 80px)' }}
      draggable={false}
    />
  )
}

// ─── Main viewer ─────────────────────────────────────────────────────────────

interface MediaViewerProps {
  allIds?: string[]
}

export function MediaViewer({ allIds = [] }: MediaViewerProps) {
  const { viewerMediaId, closeViewer, openViewer, addToast } = useUIStore()
  const { data: media } = useMediaDetail(viewerMediaId)
  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()
  const [showInfo, setShowInfo] = useState(false)

  const currentIndex = viewerMediaId ? allIds.indexOf(viewerMediaId) : -1

  const goNext = useCallback(() => {
    if (currentIndex < allIds.length - 1) openViewer(allIds[currentIndex + 1])
  }, [currentIndex, allIds, openViewer])

  const goPrev = useCallback(() => {
    if (currentIndex > 0) openViewer(allIds[currentIndex - 1])
  }, [currentIndex, allIds, openViewer])

  // Keyboard navigation
  useEffect(() => {
    if (!viewerMediaId) return
    const handleKey = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'Escape': closeViewer(); break
        case 'ArrowRight': goNext(); break
        case 'ArrowLeft': goPrev(); break
        case 'i': setShowInfo((s) => !s); break
      }
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [viewerMediaId, closeViewer, goNext, goPrev])

  const handleFavorite = async () => {
    if (!media) return
    await favoriteMedia.mutateAsync({ id: media.FileID, favorite: !media.Favorite })
    addToast({ type: 'success', message: media.Favorite ? 'Removed from favorites' : 'Added to favorites' })
  }

  const handleDelete = async () => {
    if (!media) return
    await deleteMedia.mutateAsync(media.FileID)
    addToast({ type: 'success', message: 'Moved to trash' })
    closeViewer()
  }

  return (
    <AnimatePresence>
      {viewerMediaId && (
        <motion.div
          key="viewer"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          className="fixed inset-0 z-50 flex flex-col bg-black/95"
          onClick={(e) => { if (e.target === e.currentTarget) closeViewer() }}
        >
          {/* Toolbar */}
          <div className="flex items-center justify-between px-4 h-12 flex-shrink-0">
            <Button size="icon" variant="ghost" onClick={closeViewer} aria-label="Close">
              <X size={16} />
            </Button>

            <span className="text-xs text-[--color-text-muted]">
              {media?.Filename}
            </span>

            <div className="flex items-center gap-1">
              <Button
                size="icon"
                variant="ghost"
                onClick={handleFavorite}
                aria-label={media?.Favorite ? 'Unfavorite' : 'Favorite'}
              >
                <Heart
                  size={15}
                  className={media?.Favorite ? 'fill-[--color-text-primary] text-[--color-text-primary]' : ''}
                />
              </Button>
              <Button size="icon" variant="ghost" onClick={() => setShowInfo((s) => !s)} aria-label="Info">
                <Info size={15} />
              </Button>
              <Button size="icon" variant="ghost" onClick={handleDelete} aria-label="Delete">
                <Trash2 size={15} className="text-[--color-danger]" />
              </Button>
            </div>
          </div>

          {/* Content area */}
          <div className="flex flex-1 overflow-hidden">
            {/* Prev arrow */}
            <div className="flex items-center px-3">
              {currentIndex > 0 && (
                <Button size="icon" variant="ghost" onClick={goPrev} aria-label="Previous">
                  <ChevronLeft size={18} />
                </Button>
              )}
            </div>

            {/* Image */}
            <div className="flex-1 flex items-center justify-center overflow-hidden">
              {media && <ViewerImage media={media} />}
            </div>

            {/* Next arrow */}
            <div className="flex items-center px-3">
              {currentIndex < allIds.length - 1 && (
                <Button size="icon" variant="ghost" onClick={goNext} aria-label="Next">
                  <ChevronRight size={18} />
                </Button>
              )}
            </div>

            {/* Metadata panel */}
            <AnimatePresence>
              {showInfo && media && (
                <motion.div
                  key="info"
                  initial={{ opacity: 0, x: 16 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 16 }}
                  transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                >
                  <MetadataPanel media={media} />
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Counter */}
          {allIds.length > 1 && (
            <div className="text-center py-3">
              <span className="text-xs text-[--color-text-disabled] tabular-nums">
                {currentIndex + 1} / {allIds.length}
              </span>
            </div>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  )
}

MEOF_f6ff93a7dfda

echo '  src/features/search/SearchPage.tsx'
write_file "src/features/search/SearchPage.tsx" << 'MEOF_6ef9226305c6'
import { useState, useDeferredValue } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { Search, X, Image, Video, Star } from 'lucide-react'
import { useMediaSearch } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { AuthImage } from '@/components/shared/AuthImage'
import { MediaViewer } from '@/features/viewer/MediaViewer'
import { cn } from '@/lib/utils'
import type { MediaSearchParams } from '@/types'

type FilterKey = 'all' | 'images' | 'videos' | 'favorites'

const FILTERS: { key: FilterKey; label: string; icon: React.ReactNode }[] = [
  { key: 'all', label: 'All', icon: null },
  { key: 'images', label: 'Photos', icon: <Image size={12} /> },
  { key: 'videos', label: 'Videos', icon: <Video size={12} /> },
  { key: 'favorites', label: 'Favorites', icon: <Star size={12} /> },
]

function buildParams(query: string, filter: FilterKey): MediaSearchParams {
  const params: MediaSearchParams = { sort: 'uploaded_at', order: 'desc' }
  if (query) params.query = query
  if (filter === 'images') params.mime_type = 'image/'
  if (filter === 'videos') params.mime_type = 'video/'
  if (filter === 'favorites') params.favorite = true
  return params
}

export function SearchPage() {
  const [rawQuery, setRawQuery] = useState('')
  const [filter, setFilter] = useState<FilterKey>('all')
  const { openViewer } = useUIStore()

  const query = useDeferredValue(rawQuery)
  const params = buildParams(query, filter)

  const { data, isLoading, isFetching } = useMediaSearch(params)
  const results = data?.media ?? []
  const allIds = results.map((m) => m.FileID)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-[--color-border-subtle] space-y-3">
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <Input
              value={rawQuery}
              onChange={(e) => setRawQuery(e.target.value)}
              placeholder="Search photos, videos, filenames…"
              leftIcon={
                isFetching
                  ? <span className="animate-spin h-3 w-3 border-2 border-current border-t-transparent rounded-full" />
                  : <Search size={13} />
              }
              autoFocus
            />
          </div>
          {rawQuery && (
            <Button size="icon" variant="ghost" onClick={() => setRawQuery('')} aria-label="Clear">
              <X size={14} />
            </Button>
          )}
        </div>

        {/* Filter chips */}
        <div className="flex items-center gap-1">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              onClick={() => setFilter(f.key)}
              className={cn(
                'flex items-center gap-1.5 px-3 h-6 rounded-full text-xs font-medium',
                'transition-colors duration-[120ms]',
                filter === f.key
                  ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
                  : 'text-[--color-text-muted] hover:bg-[--color-surface-overlay] hover:text-[--color-text-secondary]',
              )}
            >
              {f.icon}
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Results */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid gap-1" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
            {Array.from({ length: 15 }).map((_, i) => (
              <div key={i} className="skeleton aspect-square rounded-sm" />
            ))}
          </div>
        ) : results.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
            <Search size={28} className="text-[--color-text-disabled]" />
            <div>
              <p className="text-sm text-[--color-text-secondary]">
                {rawQuery ? `No results for "${rawQuery}"` : 'Start typing to search'}
              </p>
              <p className="text-xs text-[--color-text-muted] mt-1">
                Search by filename, type, or use date filters
              </p>
            </div>
          </div>
        ) : (
          <>
            <p className="text-xs text-[--color-text-muted] mb-3">
              {results.length} result{results.length > 1 ? 's' : ''}
            </p>
            <AnimatePresence mode="wait">
              <motion.div
                key={`${query}-${filter}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.12 }}
                className="grid gap-1"
                style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}
              >
                {results.map((media) => (
                  <button
                    key={media.FileID}
                    className="aspect-square rounded-sm overflow-hidden relative group cursor-pointer active:scale-[0.97] active:transition-transform active:duration-[80ms]"
                    onClick={() => openViewer(media.FileID)}
                  >
                    {media.ThumbnailAvailable ? (
                      <AuthImage
                        mediaId={media.FileID}
                        type="thumbnail"
                        alt={media.Filename}
                        className="absolute inset-0 w-full h-full object-cover"
                      />
                    ) : (
                      <div className="absolute inset-0 bg-[--color-surface-overlay] flex items-center justify-center">
                        <span className="text-[10px] text-[--color-text-disabled]">
                          {media.Extension.toUpperCase()}
                        </span>
                      </div>
                    )}
                    <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </button>
                ))}
              </motion.div>
            </AnimatePresence>
          </>
        )}
      </div>

      <MediaViewer allIds={allIds} />
    </div>
  )
}

MEOF_6ef9226305c6

echo '  src/features/favorites/FavoritesPage.tsx'
write_file "src/features/favorites/FavoritesPage.tsx" << 'MEOF_94d2effd22cb'
import { useMemo } from 'react'
import { Heart } from 'lucide-react'
import { useMediaSearch } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { AuthImage } from '@/components/shared/AuthImage'
import { MediaViewer } from '@/features/viewer/MediaViewer'

export function FavoritesPage() {
  const { data, isLoading } = useMediaSearch({ favorite: true, sort: 'taken_at', order: 'desc', limit: 200 })
  const { openViewer } = useUIStore()
  const items = data?.media ?? []
  const allIds = useMemo(() => items.map((m) => m.FileID), [items])

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Heart size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Favorites</h1>
        {items.length > 0 && (
          <span className="text-xs text-[--color-text-muted]">· {items.length}</span>
        )}
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid gap-1" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
            {Array.from({ length: 10 }).map((_, i) => (
              <div key={i} className="skeleton aspect-square rounded-sm" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
            <Heart size={28} className="text-[--color-text-disabled]" />
            <p className="text-sm text-[--color-text-secondary]">No favorites yet</p>
            <p className="text-xs text-[--color-text-muted]">
              Hover any photo and click the heart to add it here.
            </p>
          </div>
        ) : (
          <div className="grid gap-1" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
            {items.map((media) => (
              <button
                key={media.FileID}
                className="aspect-square rounded-sm overflow-hidden relative group cursor-pointer active:scale-[0.97] active:transition-transform active:duration-[80ms]"
                onClick={() => openViewer(media.FileID)}
              >
                {media.ThumbnailAvailable ? (
                  <AuthImage
                    mediaId={media.FileID}
                    type="thumbnail"
                    alt={media.Filename}
                    className="absolute inset-0 w-full h-full object-cover"
                  />
                ) : (
                  <div className="absolute inset-0 bg-[--color-surface-overlay]" />
                )}
                <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                <div className="absolute bottom-1.5 right-1.5">
                  <Heart size={11} className="text-white fill-white drop-shadow" />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      <MediaViewer allIds={allIds} />
    </div>
  )
}

MEOF_94d2effd22cb

echo '  src/features/trash/TrashPage.tsx'
write_file "src/features/trash/TrashPage.tsx" << 'MEOF_f292e13e3308'
import { Trash2 } from 'lucide-react'

export function TrashPage() {
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Trash2 size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Trash</h1>
      </div>
      <div className="flex-1 flex flex-col items-center justify-center gap-4 text-center p-12">
        <div className="w-14 h-14 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
          <Trash2 size={22} className="text-[--color-text-disabled]" />
        </div>
        <div className="space-y-1 max-w-xs">
          <h3 className="text-sm font-medium text-[--color-text-primary]">Trash view coming soon</h3>
          <p className="text-xs text-[--color-text-muted]">
            The backend supports soft deletion, but the API does not yet expose a dedicated
            endpoint to list deleted items. Deleted photos are retained on disk and will
            appear here once the backend adds a{' '}
            <code className="text-[--color-text-secondary] text-[11px]">deleted=true</code> filter to{' '}
            <code className="text-[--color-text-secondary] text-[11px]">GET /media</code>.
          </p>
        </div>
      </div>
    </div>
  )
}

MEOF_f292e13e3308

echo '  src/features/sync/SyncPage.tsx'
write_file "src/features/sync/SyncPage.tsx" << 'MEOF_d445a6c890aa'
import { useState } from 'react'
import { RefreshCw, CheckCircle2, AlertCircle, Clock } from 'lucide-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { getSyncDiff, ackSync } from '@/api/client'
import { Button } from '@/components/ui/Button'
import { formatRelative, formatBytes } from '@/lib/utils'
import { useUIStore } from '@/stores/ui'
import type { SyncFile } from '@/types'

function SyncFileRow({ file }: { file: SyncFile }) {
  const takenDate = new Date(file.uploaded_at)
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 hover:bg-[--color-surface-overlay] transition-colors rounded-[--radius-md]">
      <div className="w-7 h-7 rounded-[--radius-sm] bg-[--color-surface-subtle] flex items-center justify-center text-[9px] font-bold text-[--color-text-muted] flex-shrink-0">
        {file.filename.split('.').pop()?.toUpperCase().slice(0, 4) ?? 'FILE'}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs text-[--color-text-primary] truncate">{file.filename}</p>
        <p className="text-[11px] text-[--color-text-muted]">
          {formatBytes(file.size_bytes)} · {formatRelative(takenDate)}
          {file.thumbnail_available && ' · thumb'}
        </p>
      </div>
      <div className="flex gap-1 flex-shrink-0">
        {file.thumbnail_available && (
          <span className="text-[10px] bg-[--color-surface-subtle] text-[--color-text-muted] px-1.5 py-0.5 rounded-full">T</span>
        )}
        {file.preview_available && (
          <span className="text-[10px] bg-[--color-surface-subtle] text-[--color-text-muted] px-1.5 py-0.5 rounded-full">P</span>
        )}
      </div>
    </div>
  )
}

export function SyncPage() {
  const { addToast } = useUIStore()
  const [since, setSince] = useState<number | undefined>(undefined)

  const {
    data,
    isLoading,
    isFetching,
    refetch,
    dataUpdatedAt,
  } = useQuery({
    queryKey: ['sync-diff', since],
    queryFn: () => getSyncDiff(since, 100),
    staleTime: 30_000,
  })

  const ackMutation = useMutation({
    mutationFn: (ids: string[]) => ackSync(ids),
    onSuccess: (result) => {
      addToast({ type: 'success', message: `Acknowledged ${result.acknowledged} file${result.acknowledged !== 1 ? 's' : ''}` })
      refetch()
    },
    onError: () => {
      addToast({ type: 'error', message: 'Failed to acknowledge sync' })
    },
  })

  const files = data?.files ?? []
  const hasMore = data?.next_since !== undefined

  const handleAckAll = () => {
    if (files.length === 0) return
    ackMutation.mutate(files.map((f) => f.file_id))
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <RefreshCw size={15} className={`text-[--color-text-muted] ${isFetching ? 'animate-spin' : ''}`} />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Sync</h1>
        </div>
        <div className="flex items-center gap-2">
          {dataUpdatedAt > 0 && (
            <span className="text-xs text-[--color-text-disabled]">
              Updated {formatRelative(new Date(dataUpdatedAt))}
            </span>
          )}
          <Button size="sm" variant="ghost" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw size={12} className={isFetching ? 'animate-spin' : ''} />
            Refresh
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {/* Stats bar */}
        <div className="px-6 py-4 grid grid-cols-3 gap-4 border-b border-[--color-border-subtle]">
          {[
            { label: 'Pending', value: files.length, icon: <Clock size={14} /> },
            { label: 'Status', value: isFetching ? 'Syncing' : 'Idle', icon: <RefreshCw size={14} /> },
            {
              label: 'Has more',
              value: hasMore ? 'Yes' : 'No',
              icon: hasMore ? <AlertCircle size={14} className="text-[--color-warning]" /> : <CheckCircle2 size={14} className="text-[--color-success]" />,
            },
          ].map(({ label, value, icon }) => (
            <div key={label} className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4">
              <div className="flex items-center gap-1.5 text-[--color-text-muted] mb-1">
                {icon}
                <span className="text-xs">{label}</span>
              </div>
              <p className="text-lg font-semibold text-[--color-text-primary] tabular-nums">{value}</p>
            </div>
          ))}
        </div>

        {/* Actions */}
        {files.length > 0 && (
          <div className="px-6 py-3 flex items-center justify-between border-b border-[--color-border-subtle]">
            <p className="text-xs text-[--color-text-muted]">
              {files.length} unsynced file{files.length !== 1 ? 's' : ''}
              {hasMore ? ' (more available)' : ''}
            </p>
            <Button
              size="sm"
              variant="accent"
              onClick={handleAckAll}
              loading={ackMutation.isPending}
            >
              <CheckCircle2 size={12} />
              Acknowledge all
            </Button>
          </div>
        )}

        {/* File list */}
        {isLoading ? (
          <div className="p-6 space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-10 skeleton rounded-[--radius-md]" />
            ))}
          </div>
        ) : files.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
            <CheckCircle2 size={28} className="text-[--color-success]" />
            <div>
              <p className="text-sm font-medium text-[--color-text-primary]">All caught up</p>
              <p className="text-xs text-[--color-text-muted] mt-1">
                This device is synchronized with the server.
              </p>
            </div>
          </div>
        ) : (
          <div className="p-2">
            {files.map((file) => (
              <SyncFileRow key={file.file_id} file={file} />
            ))}
            {hasMore && data?.next_since && (
              <div className="px-4 py-3 text-center">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setSince(data.next_since)}
                >
                  Load more
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

MEOF_d445a6c890aa

echo '  src/features/devices/DevicesPage.tsx'
write_file "src/features/devices/DevicesPage.tsx" << 'MEOF_aecb40c0a044'
import { useState } from 'react'
import { motion } from 'motion/react'
import { Monitor, Globe, Plus } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getHealth, registerDevice } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUIStore } from '@/stores/ui'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

export function DevicesPage() {
  const { session } = useAuthStore()
  const { addToast } = useUIStore()
  const [showRegister, setShowRegister] = useState(false)
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<'web' | 'mac' | 'ios' | 'android'>('web')
  const [registering, setRegistering] = useState(false)
  const [newToken, setNewToken] = useState<string | null>(null)

  const { data: health, isError } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 30_000,
    retry: 2,
  })

  const handleRegisterNew = async () => {
    if (!newName.trim()) return
    setRegistering(true)
    try {
      const result = await registerDevice(newName.trim(), newType)
      setNewToken(result.auth_token)
      setNewName('')
      addToast({ type: 'success', message: 'Device registered — copy the token now!' })
      setShowRegister(false)
    } catch {
      addToast({ type: 'error', message: 'Registration failed' })
    } finally {
      setRegistering(false)
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <Monitor size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Devices</h1>
        </div>
        <Button size="sm" variant="ghost" onClick={() => setShowRegister((s) => !s)}>
          <Plus size={12} />
          Register new
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* Server status */}
        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Server</h2>
          <div className="flex items-center gap-3">
            <div className={`w-2 h-2 rounded-full ${isError ? 'bg-[--color-danger]' : 'bg-[--color-success]'}`} />
            <span className="text-sm text-[--color-text-primary]">{session?.serverUrl}</span>
            <span className={`text-xs ml-auto ${isError ? 'text-[--color-danger]' : 'text-[--color-success]'}`}>
              {isError ? 'Unreachable' : health?.status === 'ok' ? 'Healthy' : 'Checking…'}
            </span>
          </div>
        </div>

        {/* Current device */}
        {session && (
          <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3">
            <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">This device</h2>
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-[--radius-md] bg-[--color-surface-subtle] flex items-center justify-center text-[--color-text-secondary]">
                <Globe size={15} />
              </div>
              <div>
                <p className="text-sm text-[--color-text-primary]">{session.deviceName}</p>
                <p className="text-xs text-[--color-text-muted] font-mono">{session.deviceId.slice(0, 8)}…</p>
              </div>
              <span className="ml-auto text-xs bg-[--color-surface-subtle] text-[--color-text-muted] px-2 py-0.5 rounded-full">web</span>
            </div>
          </div>
        )}

        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-2">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Other devices</h2>
          <p className="text-xs text-[--color-text-muted]">
            The backend does not yet expose a <code className="text-[--color-text-secondary]">GET /devices</code> endpoint.
            Once added, all registered devices and their last-seen timestamps will appear here.
          </p>
        </div>

        {showRegister && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3 border border-[--color-border-default]"
          >
            <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Register a device</h2>
            <p className="text-xs text-[--color-text-muted]">
              The auth token is shown <strong className="text-[--color-text-secondary]">only once</strong> — copy it immediately.
            </p>
            <div className="space-y-2">
              <Input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Device name (e.g. Karthik's iPhone)" />
              <select
                value={newType}
                onChange={(e) => setNewType(e.target.value as typeof newType)}
                className="w-full px-3 h-9 text-sm bg-[--color-surface-subtle] text-[--color-text-primary] border border-[--color-border-default] rounded-[--radius-md] focus:outline-none"
              >
                <option value="web">Web</option>
                <option value="mac">Mac</option>
                <option value="ios">iOS</option>
                <option value="android">Android</option>
              </select>
              <Button variant="accent" size="sm" className="w-full" onClick={handleRegisterNew} loading={registering} disabled={!newName.trim()}>
                Register
              </Button>
            </div>
          </motion.div>
        )}

        {newToken && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="bg-[--color-danger-surface] border border-red-900 rounded-[--radius-lg] p-4 space-y-2"
          >
            <p className="text-xs font-semibold text-[--color-danger]">⚠ Copy this token now — it won't be shown again</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 text-xs text-[--color-text-secondary] bg-[--color-surface-base] px-3 py-2 rounded-[--radius-md] font-mono break-all select-all">
                {newToken}
              </code>
              <Button size="sm" variant="ghost" onClick={() => { navigator.clipboard.writeText(newToken); addToast({ type: 'success', message: 'Token copied!' }) }}>
                Copy
              </Button>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setNewToken(null)} className="w-full text-[--color-text-muted]">
              I've saved the token
            </Button>
          </motion.div>
        )}
      </div>
    </div>
  )
}

MEOF_aecb40c0a044

echo '  src/features/vaults/VaultsPage.tsx'
write_file "src/features/vaults/VaultsPage.tsx" << 'MEOF_f4df66dd1037'
import { useState } from 'react'
import { motion } from 'motion/react'
import { Lock, ShieldCheck, Plus, Key } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { createVault } from '@/api/client'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'
import type { VaultCreateResponse } from '@/types'

function VaultCard({ vault }: { vault: VaultCreateResponse }) {
  // note: uses useUIStore.getState() for imperative access
  const isEncrypted = vault.type === 'encrypted'

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
      className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3 border border-[--color-border-default]"
    >
      <div className="flex items-center gap-3">
        <div className={`w-8 h-8 rounded-[--radius-md] flex items-center justify-center ${
          isEncrypted ? 'bg-amber-950 text-amber-400' : 'bg-[--color-surface-subtle] text-[--color-text-muted]'
        }`}>
          {isEncrypted ? <ShieldCheck size={15} /> : <Lock size={15} />}
        </div>
        <div>
          <p className="text-sm font-medium text-[--color-text-primary]">
            {isEncrypted ? 'Encrypted vault' : 'Legacy vault'}
          </p>
          <p className="text-xs text-[--color-text-muted] font-mono">{vault.vault_id.slice(0, 16)}…</p>
        </div>
        <span className={`ml-auto text-xs px-2 py-0.5 rounded-full ${
          isEncrypted
            ? 'bg-amber-950 text-amber-400'
            : 'bg-[--color-surface-subtle] text-[--color-text-muted]'
        }`}>
          {vault.type}
        </span>
      </div>

      {isEncrypted && vault.salt && (
        <div className="space-y-1.5 pt-1">
          <p className="text-xs text-amber-400 font-semibold">⚠ Save these parameters — required for decryption</p>
          <div className="bg-[--color-surface-base] rounded-[--radius-md] p-3 space-y-1.5 font-mono">
            <div className="flex gap-2">
              <span className="text-[11px] text-[--color-text-muted] w-20">Salt</span>
              <code className="text-[11px] text-[--color-text-secondary] break-all flex-1 select-all">{vault.salt}</code>
            </div>
            {vault.argon2 && (
              <>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Argon2 time</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.time}</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Memory</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.memory_kib} KiB</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Threads</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.threads}</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Algorithm v</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.algorithm_version}</code>
                </div>
              </>
            )}
          </div>
          <Button
            size="sm"
            variant="ghost"
            className="w-full"
            onClick={() => {
              navigator.clipboard.writeText(JSON.stringify(vault, null, 2))
              useUIStore.getState().addToast({ type: 'success', message: 'Vault parameters copied!' })
            }}
          >
            <Key size={12} />
            Copy all parameters
          </Button>
        </div>
      )}
    </motion.div>
  )
}

export function VaultsPage() {
  const { addToast } = useUIStore()
  const [vaults, setVaults] = useState<VaultCreateResponse[]>([])

  const createMutation = useMutation({
    mutationFn: createVault,
    onSuccess: (vault) => {
      setVaults((v) => [vault, ...v])
      addToast({
        type: 'success',
        message: vault.type === 'encrypted'
          ? 'Encrypted vault created — save your parameters!'
          : 'Legacy vault created',
      })
    },
    onError: () => addToast({ type: 'error', message: 'Failed to create vault' }),
  })

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <Lock size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Vaults</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => createMutation.mutate('legacy')}
            loading={createMutation.isPending && createMutation.variables === 'legacy'}
          >
            <Plus size={12} />
            Legacy
          </Button>
          <Button
            size="sm"
            variant="accent"
            onClick={() => createMutation.mutate('encrypted')}
            loading={createMutation.isPending && createMutation.variables === 'encrypted'}
          >
            <ShieldCheck size={12} />
            Encrypted
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {/* Explainer */}
        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-2">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">
            About vaults
          </h2>
          <div className="space-y-1.5 text-xs text-[--color-text-muted]">
            <p>
              <span className="text-[--color-text-secondary] font-medium">Legacy vaults</span> — access-controlled hidden storage, excluded from normal gallery and sync.
            </p>
            <p>
              <span className="text-[--color-text-secondary] font-medium">Encrypted vaults</span> — client-owned Argon2id key derivation + AES-256-GCM. The server never sees your passphrase or plaintext content.
            </p>
            <p className="text-[--color-text-disabled]">
              Note: vault listing and file management within vaults are pending backend implementation.
            </p>
          </div>
        </div>

        {/* Vault list */}
        {vaults.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
            <div className="w-12 h-12 rounded-[--radius-xl] bg-[--color-surface-overlay] flex items-center justify-center">
              <Lock size={20} className="text-[--color-text-disabled]" />
            </div>
            <p className="text-sm text-[--color-text-secondary]">No vaults created yet</p>
            <p className="text-xs text-[--color-text-muted] max-w-48">
              Create a legacy or encrypted vault using the buttons above.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {vaults.map((v) => (
              <VaultCard key={v.vault_id} vault={v} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

MEOF_f4df66dd1037

echo '  src/features/settings/SettingsPage.tsx'
write_file "src/features/settings/SettingsPage.tsx" << 'MEOF_f2c92e15807d'
import { useState } from 'react'
import { Settings, LogOut } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getHealth } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest px-1">
        {title}
      </h2>
      <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] overflow-hidden">
        {children}
      </div>
    </div>
  )
}

function Row({
  label,
  value,
  mono,
  action,
}: {
  label: string
  value?: string
  mono?: boolean
  action?: React.ReactNode
}) {
  return (
    <div className="flex items-center gap-4 px-4 py-3 border-b border-[--color-border-subtle] last:border-0">
      <span className="text-xs text-[--color-text-muted] w-28 flex-shrink-0">{label}</span>
      {value && (
        <span className={`text-xs text-[--color-text-secondary] flex-1 ${mono ? 'font-mono' : ''} truncate`}>
          {value}
        </span>
      )}
      {action && <div className="ml-auto flex-shrink-0">{action}</div>}
    </div>
  )
}

export function SettingsPage() {
  const { session, clearSession } = useAuthStore()
  const { addToast } = useUIStore()
  const [confirming, setConfirming] = useState(false)

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: 1,
  })

  const handleLogout = () => {
    if (!confirming) {
      setConfirming(true)
      return
    }
    clearSession()
    addToast({ type: 'info', message: 'Session cleared' })
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Settings size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Settings</h1>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6 max-w-xl">
        {/* Connection */}
        <Section title="Connection">
          <Row label="Server URL" value={session?.serverUrl} mono />
          <Row
            label="Server status"
            value={health?.status === 'ok' ? 'Healthy' : 'Unreachable'}
            action={
              <span className={`w-2 h-2 rounded-full ${health?.status === 'ok' ? 'bg-[--color-success]' : 'bg-[--color-danger]'}`} />
            }
          />
        </Section>

        {/* Session */}
        <Section title="Session">
          <Row label="Device name" value={session?.deviceName} />
          <Row label="Device ID" value={session?.deviceId} mono />
          <Row
            label="Auth token"
            value="••••••••••••••••"
            mono
            action={
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  if (session?.authToken) {
                    navigator.clipboard.writeText(session.authToken)
                    addToast({ type: 'success', message: 'Token copied' })
                  }
                }}
              >
                Copy
              </Button>
            }
          />
        </Section>

        {/* About */}
        <Section title="About">
          <Row label="App" value="Mnemos" />
          <Row label="Backend" value="PhotoVault (Go)" />
          <Row
            label="API"
            value="v1 — device, upload, sync, media, vaults"
          />
        </Section>

        {/* Danger zone */}
        <Section title="Danger zone">
          <div className="px-4 py-3">
            <p className="text-xs text-[--color-text-muted] mb-3">
              Clearing the session removes the auth token from this browser.
              Your photos remain safe on the server. You'll need to register again to reconnect.
            </p>
            <Button
              variant={confirming ? 'destructive' : 'outline'}
              size="sm"
              onClick={handleLogout}
              onBlur={() => setConfirming(false)}
            >
              <LogOut size={12} />
              {confirming ? 'Click again to confirm' : 'Clear session'}
            </Button>
          </div>
        </Section>
      </div>
    </div>
  )
}

MEOF_f2c92e15807d

echo '  src/features/storage/FirstRunWizard.tsx'
write_file "src/features/storage/FirstRunWizard.tsx" << 'MEOF_cf25434fa0c5'
import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { HardDrive, FolderOpen, ArrowRight, Check } from 'lucide-react'
import { useCreateStorage, useSelectFolder } from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { cn } from '@/lib/utils'

type Step = 'name' | 'folder' | 'confirm'

interface WizardState { name: string; path: string }

function StepDot({ active, done }: { active: boolean; done: boolean }) {
  return (
    <div className={cn(
      'w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold transition-colors duration-[200ms]',
      done ? 'bg-[--color-success] text-white'
           : active ? 'bg-[--color-accent] text-[--color-surface-base]'
           : 'bg-[--color-surface-subtle] text-[--color-text-muted]',
    )}>
      {done ? <Check size={10} /> : null}
    </div>
  )
}

export function FirstRunWizard() {
  const { addToast } = useUIStore()
  const [step, setStep] = useState<Step>('name')
  const [state, setState] = useState<WizardState>({ name: 'Main Library', path: '' })
  const [direction, setDirection] = useState(1)
  const [manualPath, setManualPath] = useState('')

  const selectFolder = useSelectFolder()
  const createStorage = useCreateStorage()

  const goTo = (next: Step, dir = 1) => { setDirection(dir); setStep(next) }

  const handleSelectFolder = async () => {
    try {
      const result = await selectFolder.mutateAsync()
      setState((s) => ({ ...s, path: result.path }))
      goTo('confirm')
    } catch {
      // Backend folder picker not available — user falls back to manual input
      addToast({ type: 'info', message: 'Folder picker unavailable — enter path manually below.' })
    }
  }

  const handleManualContinue = () => {
    const p = manualPath.trim()
    if (!p) return
    setState((s) => ({ ...s, path: p }))
    goTo('confirm')
  }

  const handleCreate = async () => {
    if (!state.name.trim() || !state.path) return
    try {
      await createStorage.mutateAsync({ name: state.name.trim(), path: state.path })
      addToast({ type: 'success', message: `"${state.name}" library created` })
    } catch {
      addToast({ type: 'error', message: 'Failed to create library — check server logs.' })
    }
  }

  const stepIndex: Record<Step, number> = { name: 0, folder: 1, confirm: 2 }
  const currentIndex = stepIndex[step]

  const variants = {
    enter: (d: number) => ({ opacity: 0, x: d > 0 ? 24 : -24 }),
    center: { opacity: 1, x: 0 },
    exit:  (d: number) => ({ opacity: 0, x: d > 0 ? -24 : 24 }),
  }

  return (
    <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.3 }}
        className="w-full max-w-md"
      >
        {/* Header */}
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-[--radius-lg] bg-[--color-accent] flex items-center justify-center">
            <HardDrive size={17} className="text-[--color-surface-base]" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-[--color-text-primary] tracking-tight">Welcome to Mnemos</h1>
            <p className="text-xs text-[--color-text-muted]">No storage library has been configured.</p>
          </div>
        </div>

        {/* Steps */}
        <div className="flex items-center gap-2 mb-8">
          {(['name', 'folder', 'confirm'] as Step[]).map((s, i) => (
            <div key={s} className="flex items-center gap-2">
              <StepDot active={step === s} done={currentIndex > i} />
              {i < 2 && (
                <div className={cn(
                  'h-px w-8 transition-colors duration-[300ms]',
                  currentIndex > i ? 'bg-[--color-success]' : 'bg-[--color-border-default]',
                )} />
              )}
            </div>
          ))}
          <div className="flex-1" />
          <span className="text-xs text-[--color-text-muted]">Step {currentIndex + 1} of 3</span>
        </div>

        {/* Content */}
        <div className="relative min-h-64">
          <AnimatePresence mode="wait" custom={direction}>
            {step === 'name' && (
              <motion.div key="name" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-6"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Name your library</h2>
                  <p className="text-sm text-[--color-text-secondary]">Give this storage library a name. You can rename it later.</p>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-[--color-text-secondary]">Library name</label>
                  <Input value={state.name} onChange={(e) => setState((s) => ({ ...s, name: e.target.value }))}
                    placeholder="Main Library" autoFocus />
                </div>
                <Button variant="accent" size="lg" className="w-full"
                  onClick={() => goTo('folder')} disabled={!state.name.trim()}>
                  Continue <ArrowRight size={14} />
                </Button>
              </motion.div>
            )}

            {step === 'folder' && (
              <motion.div key="folder" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-4"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Choose a folder</h2>
                  <p className="text-sm text-[--color-text-secondary]">
                    Select where photos will be stored on the server.
                  </p>
                </div>

                {/* Option A: server-side picker */}
                <Button variant="outline" size="lg" className="w-full"
                  onClick={handleSelectFolder} loading={selectFolder.isPending}>
                  <FolderOpen size={14} />
                  Open folder picker on server
                </Button>

                {/* Divider */}
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-px bg-[--color-border-default]" />
                  <span className="text-xs text-[--color-text-disabled]">or enter path manually</span>
                  <div className="flex-1 h-px bg-[--color-border-default]" />
                </div>

                {/* Option B: manual path */}
                <div className="space-y-2">
                  <Input
                    value={manualPath}
                    onChange={(e) => setManualPath(e.target.value)}
                    placeholder="/Volumes/Photos or /Users/you/Pictures"
                    className="font-mono text-xs"
                  />
                  <Button variant="accent" size="lg" className="w-full"
                    onClick={handleManualContinue} disabled={!manualPath.trim()}>
                    Continue <ArrowRight size={14} />
                  </Button>
                </div>

                <Button variant="ghost" size="sm" className="w-full" onClick={() => goTo('name', -1)}>
                  Back
                </Button>
              </motion.div>
            )}

            {step === 'confirm' && (
              <motion.div key="confirm" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-6"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Confirm library</h2>
                  <p className="text-sm text-[--color-text-secondary]">Review before creating.</p>
                </div>
                <div className="bg-[--color-surface-overlay] rounded-[--radius-xl] border border-[--color-border-default] divide-y divide-[--color-border-subtle]">
                  <div className="flex items-start gap-3 px-4 py-3">
                    <span className="text-xs text-[--color-text-muted] w-16 flex-shrink-0 pt-0.5">Name</span>
                    <span className="text-sm text-[--color-text-primary] font-medium">{state.name}</span>
                  </div>
                  <div className="flex items-start gap-3 px-4 py-3">
                    <span className="text-xs text-[--color-text-muted] w-16 flex-shrink-0 pt-0.5">Folder</span>
                    <span className="text-xs text-[--color-text-secondary] font-mono break-all">{state.path}</span>
                  </div>
                </div>
                <Button variant="accent" size="lg" className="w-full"
                  onClick={handleCreate} loading={createStorage.isPending}>
                  Create Library
                </Button>
                <Button variant="ghost" size="sm" className="w-full" onClick={() => goTo('folder', -1)}>
                  Back
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  )
}
MEOF_cf25434fa0c5

echo '  src/features/storage/LibraryCard.tsx'
write_file "src/features/storage/LibraryCard.tsx" << 'MEOF_a4e51906b1e6'
import { useState } from 'react'
import { motion } from 'motion/react'
import {
  HardDrive, CheckCircle2, AlertTriangle, ChevronDown,
  Pencil, Star, ShieldCheck, RefreshCw, Trash2, Check, X,
} from 'lucide-react'
import {
  useRenameStorage,
  useSetDefaultStorage,
  useVerifyStorage,
  useRescanStorage,
  useDeleteStorage,
} from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { formatBytes, cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { StorageLibrary } from '@/types/storage'

interface LibraryCardProps {
  library: StorageLibrary
}

// ─── Inline rename input ──────────────────────────────────────────────────────

function RenameInput({
  initial,
  onSave,
  onCancel,
}: {
  initial: string
  onSave: (name: string) => void
  onCancel: () => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <form
      className="flex items-center gap-1.5"
      onSubmit={(e) => { e.preventDefault(); if (value.trim()) onSave(value.trim()) }}
    >
      <input
        autoFocus
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className={cn(
          'flex-1 px-2 h-7 text-sm bg-[--color-surface-subtle]',
          'text-[--color-text-primary] border border-[--color-border-bright]',
          'rounded-[--radius-md] focus:outline-none focus:border-[--color-accent]',
        )}
      />
      <button type="submit" className="p-1 text-[--color-success]" aria-label="Save">
        <Check size={13} />
      </button>
      <button type="button" onClick={onCancel} className="p-1 text-[--color-text-muted]" aria-label="Cancel">
        <X size={13} />
      </button>
    </form>
  )
}

// ─── Library card ─────────────────────────────────────────────────────────────

export function LibraryCard({ library }: LibraryCardProps) {
  const { addToast } = useUIStore()
  const [expanded, setExpanded] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const rename = useRenameStorage()
  const setDefault = useSetDefaultStorage()
  const verify = useVerifyStorage()
  const rescan = useRescanStorage()
  const deleteLib = useDeleteStorage()

  const handleRename = async (name: string) => {
    await rename.mutateAsync({ id: library.id, payload: { name } })
    setRenaming(false)
    addToast({ type: 'success', message: `Renamed to "${name}"` })
  }

  const handleSetDefault = async () => {
    await setDefault.mutateAsync(library.id)
    addToast({ type: 'success', message: `"${library.name}" set as default` })
  }

  const handleVerify = async () => {
    const result = await verify.mutateAsync(library.id)
    addToast({
      type: result.healthy ? 'success' : 'error',
      message: result.healthy ? `"${library.name}" is healthy` : `"${library.name}" has issues`,
    })
  }

  const handleRescan = async () => {
    await rescan.mutateAsync(library.id)
    addToast({ type: 'info', message: `Rescanning "${library.name}"…` })
  }

  const handleDelete = async () => {
    if (!confirmDelete) { setConfirmDelete(true); return }
    await deleteLib.mutateAsync(library.id)
    addToast({ type: 'success', message: `"${library.name}" removed` })
  }

  return (
    <motion.div
      layout
      className="bg-[--color-surface-overlay] rounded-[--radius-xl] border border-[--color-border-default] overflow-hidden"
    >
      {/* Main row */}
      <div className="flex items-center gap-4 px-5 py-4">
        {/* Icon */}
        <div className={cn(
          'w-9 h-9 rounded-[--radius-lg] flex-shrink-0 flex items-center justify-center',
          library.healthy ? 'bg-[--color-surface-subtle]' : 'bg-[--color-danger-surface]',
        )}>
          <HardDrive
            size={16}
            className={library.healthy ? 'text-[--color-text-secondary]' : 'text-[--color-danger]'}
          />
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          {renaming ? (
            <RenameInput
              initial={library.name}
              onSave={handleRename}
              onCancel={() => setRenaming(false)}
            />
          ) : (
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-[--color-text-primary] truncate">
                {library.name}
              </p>
              {library.default && (
                <span className="text-[10px] font-semibold text-[--color-text-muted] bg-[--color-surface-subtle] px-1.5 py-0.5 rounded-full uppercase tracking-wide">
                  Default
                </span>
              )}
            </div>
          )}
          <p className="text-xs text-[--color-text-muted] font-mono mt-0.5 truncate">
            {library.path}
          </p>
        </div>

        {/* Status + expand */}
        <div className="flex items-center gap-2 flex-shrink-0">
          {library.healthy ? (
            <div className="flex items-center gap-1.5 text-[--color-success]">
              <CheckCircle2 size={13} />
              <span className="text-xs hidden sm:block">Healthy</span>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 text-[--color-danger]">
              <AlertTriangle size={13} />
              <span className="text-xs hidden sm:block">Unavailable</span>
            </div>
          )}
          <button
            onClick={() => setExpanded((s) => !s)}
            className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
            aria-label={expanded ? 'Collapse' : 'Expand'}
          >
            <ChevronDown
              size={14}
              className={cn('transition-transform duration-[150ms]', expanded && 'rotate-180')}
            />
          </button>
        </div>
      </div>

      {/* Expanded stats + actions */}
      <motion.div
        initial={false}
        animate={{ height: expanded ? 'auto' : 0, opacity: expanded ? 1 : 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
        className="overflow-hidden"
      >
        <div className="px-5 pb-4 pt-1 border-t border-[--color-border-subtle] space-y-4">
          {/* Stats */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-[--color-surface-subtle] rounded-[--radius-md] px-3 py-2">
              <p className="text-[10px] text-[--color-text-muted] uppercase tracking-wide mb-0.5">Available</p>
              <p className="text-sm font-semibold text-[--color-text-primary]">
                {library.free_space > 0 ? formatBytes(library.free_space) : '—'}
              </p>
            </div>
            <div className="bg-[--color-surface-subtle] rounded-[--radius-md] px-3 py-2">
              <p className="text-[10px] text-[--color-text-muted] uppercase tracking-wide mb-0.5">Status</p>
              <p className={cn(
                'text-sm font-semibold',
                library.healthy ? 'text-[--color-success]' : 'text-[--color-danger]',
              )}>
                {library.healthy ? '✓ Healthy' : '⚠ Unavailable'}
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="ghost" onClick={() => setRenaming(true)} disabled={renaming}>
              <Pencil size={12} />
              Rename
            </Button>
            {!library.default && (
              <Button size="sm" variant="ghost" onClick={handleSetDefault} loading={setDefault.isPending}>
                <Star size={12} />
                Set default
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={handleVerify} loading={verify.isPending}>
              <ShieldCheck size={12} />
              Verify
            </Button>
            <Button size="sm" variant="ghost" onClick={handleRescan} loading={rescan.isPending}>
              <RefreshCw size={12} />
              Rescan
            </Button>
            <Button
              size="sm"
              variant={confirmDelete ? 'destructive' : 'ghost'}
              onClick={handleDelete}
              loading={deleteLib.isPending}
              onBlur={() => setConfirmDelete(false)}
              className="ml-auto"
            >
              <Trash2 size={12} />
              {confirmDelete ? 'Confirm remove' : 'Remove'}
            </Button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}
MEOF_a4e51906b1e6

echo '  src/features/storage/LibrarySelector.tsx'
write_file "src/features/storage/LibrarySelector.tsx" << 'MEOF_41c4efffe1bc'
import { useRef, useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { ChevronDown, CheckCircle2, AlertTriangle, HardDrive } from 'lucide-react'
import { useStorages } from '@/hooks/useStorage'
import { useStorageStore } from '@/stores/storage'
import { formatBytes, cn } from '@/lib/utils'
import type { StorageLibrary } from '@/types/storage'

// ─── Storage status icon ─────────────────────────────────────────────────────

function StatusDot({ healthy }: { healthy: boolean }) {
  return (
    <span
      className={cn(
        'w-1.5 h-1.5 rounded-full flex-shrink-0',
        healthy ? 'bg-[--color-success]' : 'bg-[--color-danger]',
      )}
    />
  )
}

// ─── Dropdown option ─────────────────────────────────────────────────────────

function LibraryOption({
  library,
  selected,
  onSelect,
}: {
  library: StorageLibrary
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      onClick={onSelect}
      className={cn(
        'w-full flex items-center gap-2.5 px-3 py-2 text-left',
        'transition-colors duration-[100ms]',
        selected
          ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
          : 'text-[--color-text-secondary] hover:bg-[--color-surface-overlay] hover:text-[--color-text-primary]',
      )}
    >
      <StatusDot healthy={library.healthy} />
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium truncate">{library.name}</p>
        {library.free_space > 0 && (
          <p className="text-[10px] text-[--color-text-disabled]">
            {formatBytes(library.free_space)} free
          </p>
        )}
      </div>
      {selected && <CheckCircle2 size={13} className="text-[--color-accent] flex-shrink-0" />}
    </button>
  )
}

// ─── Main selector ────────────────────────────────────────────────────────────

export function LibrarySelector() {
  const { data: libraries = [], isLoading } = useStorages()
  const { selectedLibraryId, setSelectedLibraryId } = useStorageStore()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // Resolve the active library — fall back to default, then first
  const activeLibrary: StorageLibrary | null =
    libraries.find((l) => l.id === selectedLibraryId) ??
    libraries.find((l) => l.default) ??
    libraries[0] ??
    null

  // Auto-set selected to default on first load
  useEffect(() => {
    if (!selectedLibraryId && activeLibrary) {
      setSelectedLibraryId(activeLibrary.id)
    }
  }, [activeLibrary, selectedLibraryId, setSelectedLibraryId])

  // Close on outside click
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    if (open) document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  if (isLoading || libraries.length === 0) return null

  const unavailable = activeLibrary && !activeLibrary.healthy

  return (
    <div className="mx-4">
      <p className="text-[10px] text-[--color-text-disabled] uppercase tracking-widest font-semibold mb-1.5">
        Destination
      </p>

      {/* Unavailable banner */}
      {unavailable && (
        <div className="flex items-center gap-2 px-3 py-2 mb-2 rounded-[--radius-md] bg-[--color-danger-surface] border border-red-900">
          <AlertTriangle size={12} className="text-[--color-danger] flex-shrink-0" />
          <p className="text-[11px] text-[--color-danger] leading-tight">
            Storage unavailable — reconnect the drive or choose another library.
          </p>
        </div>
      )}

      {/* Selector button */}
      <div ref={ref} className="relative">
        <button
          onClick={() => libraries.length > 1 && setOpen((s) => !s)}
          className={cn(
            'w-full flex items-center gap-2 px-3 h-8 rounded-[--radius-md] text-xs',
            'bg-[--color-surface-overlay] border border-[--color-border-default]',
            'transition-colors duration-[100ms]',
            libraries.length > 1
              ? 'hover:bg-[--color-surface-subtle] cursor-pointer active:scale-[0.97] active:transition-transform active:duration-[80ms]'
              : 'cursor-default',
          )}
          aria-haspopup={libraries.length > 1 ? 'listbox' : undefined}
          aria-expanded={open}
        >
          <HardDrive size={11} className="text-[--color-text-muted] flex-shrink-0" />
          {activeLibrary && <StatusDot healthy={activeLibrary.healthy} />}
          <span className="flex-1 text-left text-[--color-text-primary] truncate font-medium">
            {activeLibrary?.name ?? 'Select library'}
          </span>
          {libraries.length > 1 && (
            <ChevronDown
              size={12}
              className={cn(
                'text-[--color-text-muted] transition-transform duration-[150ms]',
                open && 'rotate-180',
              )}
            />
          )}
        </button>

        {/* Dropdown */}
        <AnimatePresence>
          {open && (
            <motion.div
              key="library-dropdown"
              initial={{ opacity: 0, y: -4, scaleY: 0.95 }}
              animate={{ opacity: 1, y: 0, scaleY: 1 }}
              exit={{ opacity: 0, y: -4, scaleY: 0.95 }}
              transition={{ type: 'spring', bounce: 0, duration: 0.18 }}
              style={{ transformOrigin: 'top' }}
              className={cn(
                'absolute top-full mt-1 left-0 right-0 z-20',
                'bg-[--color-surface-overlay] border border-[--color-border-default]',
                'rounded-[--radius-lg] shadow-[--shadow-3] overflow-hidden',
              )}
              role="listbox"
            >
              {libraries.map((lib) => (
                <LibraryOption
                  key={lib.id}
                  library={lib}
                  selected={lib.id === activeLibrary?.id}
                  onSelect={() => {
                    setSelectedLibraryId(lib.id)
                    setOpen(false)
                  }}
                />
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
MEOF_41c4efffe1bc

echo '  src/features/storage/StoragePage.tsx'
write_file "src/features/storage/StoragePage.tsx" << 'MEOF_5d903596b4b8'
import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { HardDrive, Plus, ArrowRight, FolderOpen, X } from 'lucide-react'
import { useStorages, useCreateStorage, useSelectFolder } from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { LibraryCard } from './LibraryCard'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

// ─── Add library dialog ───────────────────────────────────────────────────────

interface AddLibraryDialogProps {
  onClose: () => void
}

function AddLibraryDialog({ onClose }: AddLibraryDialogProps) {
  const { addToast } = useUIStore()
  const [name, setName] = useState('')
  const [path, setPath] = useState('')

  const selectFolder = useSelectFolder()
  const createStorage = useCreateStorage()

  const handleSelectFolder = async () => {
    try {
      const result = await selectFolder.mutateAsync()
      setPath(result.path)
    } catch {
      addToast({ type: 'error', message: 'Could not open folder picker' })
    }
  }

  const handleCreate = async () => {
    if (!name.trim() || !path) return
    try {
      await createStorage.mutateAsync({ name: name.trim(), path })
      addToast({ type: 'success', message: `"${name.trim()}" library added` })
      onClose()
    } catch {
      addToast({ type: 'error', message: 'Failed to create library' })
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 8 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
        className="w-full max-w-md bg-[--color-surface-overlay] border border-[--color-border-default] rounded-[--radius-2xl] shadow-[--shadow-4] p-6 space-y-5"
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-[--color-text-primary]">Add library</h2>
          <button
            onClick={onClose}
            className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
          >
            <X size={15} />
          </button>
        </div>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Name</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Archive"
              autoFocus
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Folder</label>
            {path ? (
              <div className="flex items-center gap-2 px-3 py-2 bg-[--color-surface-subtle] rounded-[--radius-md] border border-[--color-border-default]">
                <FolderOpen size={13} className="text-[--color-text-muted] flex-shrink-0" />
                <span className="text-xs text-[--color-text-secondary] font-mono truncate flex-1">{path}</span>
                <button
                  onClick={handleSelectFolder}
                  className="text-xs text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors flex-shrink-0"
                >
                  Change
                </button>
              </div>
            ) : (
              <Button
                variant="outline"
                size="md"
                className="w-full"
                onClick={handleSelectFolder}
                loading={selectFolder.isPending}
              >
                <FolderOpen size={13} />
                Choose folder on server
              </Button>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 pt-1">
          <Button variant="ghost" size="md" onClick={onClose} className="flex-1">Cancel</Button>
          <Button
            variant="accent"
            size="md"
            className="flex-1"
            onClick={handleCreate}
            loading={createStorage.isPending}
            disabled={!name.trim() || !path}
          >
            Create library
          </Button>
        </div>
      </motion.div>
    </motion.div>
  )
}

// ─── Storage page ─────────────────────────────────────────────────────────────

export function StoragePage() {
  const { data: libraries = [], isLoading } = useStorages()
  const [showAdd, setShowAdd] = useState(false)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <HardDrive size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Storage</h1>
          {libraries.length > 0 && (
            <span className="text-xs text-[--color-text-muted]">
              · {libraries.length} {libraries.length === 1 ? 'library' : 'libraries'}
            </span>
          )}
        </div>
        <Button size="sm" variant="ghost" onClick={() => setShowAdd(true)}>
          <Plus size={12} />
          Add library
        </Button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <div key={i} className="h-20 skeleton rounded-[--radius-xl]" />
            ))}
          </div>
        ) : libraries.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-4 text-center">
            <div className="w-14 h-14 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
              <HardDrive size={22} className="text-[--color-text-disabled]" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-medium text-[--color-text-primary]">No libraries configured</p>
              <p className="text-xs text-[--color-text-muted] max-w-48">
                Add a storage library to start uploading photos.
              </p>
            </div>
            <Button variant="accent" size="md" onClick={() => setShowAdd(true)}>
              <Plus size={13} />
              Add library
              <ArrowRight size={13} />
            </Button>
          </div>
        ) : (
          <div className="space-y-3 max-w-2xl">
            <AnimatePresence mode="popLayout">
              {libraries.map((lib) => (
                <LibraryCard key={lib.id} library={lib} />
              ))}
            </AnimatePresence>

            <p className="text-xs text-[--color-text-disabled] pt-2">
              Removing a library does not delete files from disk.
            </p>
          </div>
        )}
      </div>

      {/* Add dialog */}
      <AnimatePresence>
        {showAdd && <AddLibraryDialog onClose={() => setShowAdd(false)} />}
      </AnimatePresence>
    </div>
  )
}
MEOF_5d903596b4b8

echo '  src/layouts/AppLayout.tsx'
write_file "src/layouts/AppLayout.tsx" << 'MEOF_0a0e91cf1b2d'
import { Outlet, Link, useRouter } from '@tanstack/react-router'
import { motion, AnimatePresence } from 'motion/react'
import {
  Images,
  Clock,
  Heart,
  Search,
  Trash2,
  Upload,
  Monitor,
  Lock,
  Settings,
  RefreshCw,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Toasts } from '@/components/ui/Toasts'
import { useUploadStore } from '@/stores/upload'
import { UploadQueue } from '@/features/upload/UploadQueue'

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
  badge?: number
}

function NavLink({ to, label, icon, badge }: NavItem) {
  const router = useRouter()
  const isActive = router.state.location.pathname === to ||
    (to !== '/' && router.state.location.pathname.startsWith(to))

  return (
    <Link
      to={to}
      className={cn(
        'flex items-center gap-2.5 px-3 h-8 rounded-[--radius-md] text-sm',
        'transition-colors duration-[120ms] ease-out select-none',
        isActive
          ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
          : 'text-[--color-text-secondary] hover:bg-[--color-surface-overlay] hover:text-[--color-text-primary]',
      )}
    >
      <span className="w-4 h-4 flex-shrink-0 opacity-70">{icon}</span>
      <span className="flex-1">{label}</span>
      {badge !== undefined && badge > 0 && (
        <span className="text-xs text-[--color-text-muted] tabular-nums">{badge}</span>
      )}
    </Link>
  )
}

const NAV_SECTIONS = [
  {
    items: [
      { to: '/gallery', label: 'Library', icon: <Images size={15} /> },
      { to: '/timeline', label: 'Timeline', icon: <Clock size={15} /> },
      { to: '/search', label: 'Search', icon: <Search size={15} /> },
    ],
  },
  {
    label: 'Collections',
    items: [
      { to: '/favorites', label: 'Favorites', icon: <Heart size={15} /> },
      { to: '/trash', label: 'Trash', icon: <Trash2 size={15} /> },
    ],
  },
  {
    label: 'System',
    items: [
      { to: '/sync', label: 'Sync', icon: <RefreshCw size={15} /> },
      { to: '/devices', label: 'Devices', icon: <Monitor size={15} /> },
      { to: '/vaults', label: 'Vaults', icon: <Lock size={15} /> },
      { to: '/settings', label: 'Settings', icon: <Settings size={15} /> },
    ],
  },
]

export function AppLayout() {
  const { queue, isOpen, setOpen } = useUploadStore()
  const activeUploads = queue.filter(
    (i) => i.status === 'uploading' || i.status === 'hashing' || i.status === 'checking',
  ).length

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const files = Array.from(e.dataTransfer.files)
    if (files.length > 0) {
      useUploadStore.getState().addFiles(files)
    }
  }

  const handleDragOver = (e: React.DragEvent) => e.preventDefault()

  return (
    <div
      className="flex h-screen bg-[--color-surface-base] overflow-hidden"
      onDrop={handleDrop}
      onDragOver={handleDragOver}
    >
      {/* ── Sidebar ── */}
      <aside className="w-56 flex-shrink-0 flex flex-col border-r border-[--color-border-subtle] py-4">
        {/* Logo */}
        <div className="px-4 mb-6">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-[--radius-sm] bg-[--color-accent] flex items-center justify-center">
              <Images size={13} className="text-[--color-surface-base]" />
            </div>
            <span className="text-sm font-semibold tracking-tight text-[--color-text-primary]">
              Mnemos
            </span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-2 space-y-5 overflow-y-auto scrollbar-none">
          {NAV_SECTIONS.map((section, i) => (
            <div key={i}>
              {section.label && (
                <p className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-[--color-text-disabled]">
                  {section.label}
                </p>
              )}
              <div className="space-y-0.5">
                {section.items.map((item) => (
                  <NavLink key={item.to} {...item} />
                ))}
              </div>
            </div>
          ))}
        </nav>

        {/* Upload trigger */}
        <div className="px-2 mt-4">
          <button
            onClick={() => {
              setOpen(true)
            }}
            className={cn(
              'w-full flex items-center gap-2.5 px-3 h-8 rounded-[--radius-md] text-sm',
              'text-[--color-text-secondary] hover:bg-[--color-surface-overlay]',
              'hover:text-[--color-text-primary] transition-colors duration-[120ms]',
              'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
            )}
          >
            <Upload size={15} className="opacity-70" />
            <span className="flex-1 text-left">Upload</span>
            {activeUploads > 0 && (
              <span className="text-xs text-[--color-warning] tabular-nums">{activeUploads}</span>
            )}
          </button>
        </div>
      </aside>

      {/* ── Main content ── */}
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>

      {/* ── Upload queue panel ── */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="upload-queue"
            initial={{ opacity: 0, x: 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 16 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="w-80 flex-shrink-0 border-l border-[--color-border-subtle] overflow-hidden"
          >
            <UploadQueue onClose={() => setOpen(false)} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Toasts ── */}
      <Toasts />
    </div>
  )
}

MEOF_0a0e91cf1b2d

echo '  src/routes/__root.tsx'
write_file "src/routes/__root.tsx" << 'MEOF_cec72835a8c5'
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { AuthPage } from '@/features/auth/AuthPage'
import { useAuthStore } from '@/stores/auth'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated } = useAuthStore()

  if (!isAuthenticated) {
    return <AuthPage />
  }

  return <Outlet />
}

MEOF_cec72835a8c5

echo '  src/routes/_app.tsx'
write_file "src/routes/_app.tsx" << 'MEOF_cc17ba8c7d18'
import { createFileRoute } from '@tanstack/react-router'
import { AppLayout } from '@/layouts/AppLayout'

export const Route = createFileRoute('/_app')({
  component: AppLayout,
})

MEOF_cc17ba8c7d18

echo '  src/routes/_app/index.tsx'
write_file "src/routes/_app/index.tsx" << 'MEOF_a03707ab1067'
import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/_app/')({
  loader: () => { throw redirect({ to: '/gallery' }) },
})

MEOF_a03707ab1067

echo '  src/routes/_app/gallery.tsx'
write_file "src/routes/_app/gallery.tsx" << 'MEOF_f99e6d7245db'
import { createFileRoute } from '@tanstack/react-router'
import { GalleryPage } from '@/features/gallery/GalleryPage'

export const Route = createFileRoute('/_app/gallery')({
  component: GalleryPage,
})

MEOF_f99e6d7245db

echo '  src/routes/_app/timeline.tsx'
write_file "src/routes/_app/timeline.tsx" << 'MEOF_63621ad1a753'
import { createFileRoute } from '@tanstack/react-router'
import { TimelinePage } from '@/features/gallery/TimelinePage'

export const Route = createFileRoute('/_app/timeline')({
  component: TimelinePage,
})

MEOF_63621ad1a753

echo '  src/routes/_app/search.tsx'
write_file "src/routes/_app/search.tsx" << 'MEOF_ad13f8d7e190'
import { createFileRoute } from '@tanstack/react-router'
import { SearchPage } from '@/features/search/SearchPage'

export const Route = createFileRoute('/_app/search')({
  component: SearchPage,
})

MEOF_ad13f8d7e190

echo '  src/routes/_app/favorites.tsx'
write_file "src/routes/_app/favorites.tsx" << 'MEOF_2c71a8441fa0'
import { createFileRoute } from '@tanstack/react-router'
import { FavoritesPage } from '@/features/favorites/FavoritesPage'

export const Route = createFileRoute('/_app/favorites')({
  component: FavoritesPage,
})

MEOF_2c71a8441fa0

echo '  src/routes/_app/trash.tsx'
write_file "src/routes/_app/trash.tsx" << 'MEOF_1016135046c7'
import { createFileRoute } from '@tanstack/react-router'
import { TrashPage } from '@/features/trash/TrashPage'

export const Route = createFileRoute('/_app/trash')({
  component: TrashPage,
})

MEOF_1016135046c7

echo '  src/routes/_app/sync.tsx'
write_file "src/routes/_app/sync.tsx" << 'MEOF_3a15d1aa221a'
import { createFileRoute } from '@tanstack/react-router'
import { SyncPage } from '@/features/sync/SyncPage'

export const Route = createFileRoute('/_app/sync')({
  component: SyncPage,
})

MEOF_3a15d1aa221a

echo '  src/routes/_app/devices.tsx'
write_file "src/routes/_app/devices.tsx" << 'MEOF_5abfbaef0a61'
import { createFileRoute } from '@tanstack/react-router'
import { DevicesPage } from '@/features/devices/DevicesPage'

export const Route = createFileRoute('/_app/devices')({
  component: DevicesPage,
})

MEOF_5abfbaef0a61

echo '  src/routes/_app/vaults.tsx'
write_file "src/routes/_app/vaults.tsx" << 'MEOF_5423762c1f28'
import { createFileRoute } from '@tanstack/react-router'
import { VaultsPage } from '@/features/vaults/VaultsPage'

export const Route = createFileRoute('/_app/vaults')({
  component: VaultsPage,
})

MEOF_5423762c1f28

echo '  src/routes/_app/settings.tsx'
write_file "src/routes/_app/settings.tsx" << 'MEOF_24f1295d75d2'
import { createFileRoute } from '@tanstack/react-router'
import { SettingsPage } from '@/features/settings/SettingsPage'

export const Route = createFileRoute('/_app/settings')({
  component: SettingsPage,
})

MEOF_24f1295d75d2

echo '  src/routes/_app/storage.tsx'
write_file "src/routes/_app/storage.tsx" << 'MEOF_c182347bea34'
import { createFileRoute } from '@tanstack/react-router'
import { StoragePage } from '@/features/storage/StoragePage'

export const Route = createFileRoute('/_app/storage')({
  component: StoragePage,
})
MEOF_c182347bea34

echo '  src/routeTree.gen.ts'
write_file "src/routeTree.gen.ts" << 'MEOF_cb917d6ea6d1'
/* eslint-disable */

// @ts-nocheck

// noinspection JSUnusedGlobalSymbols

// This file was automatically generated by TanStack Router.
// You should NOT make any changes in this file as it will be overwritten.
// Additionally, you should also exclude this file from your linter and/or formatter to prevent it from being checked or modified.

import { Route as rootRouteImport } from './routes/__root'
import { Route as AppRouteImport } from './routes/_app'
import { Route as AppIndexRouteImport } from './routes/_app/index'
import { Route as AppDevicesRouteImport } from './routes/_app/devices'
import { Route as AppFavoritesRouteImport } from './routes/_app/favorites'
import { Route as AppGalleryRouteImport } from './routes/_app/gallery'
import { Route as AppSearchRouteImport } from './routes/_app/search'
import { Route as AppSettingsRouteImport } from './routes/_app/settings'
import { Route as AppSyncRouteImport } from './routes/_app/sync'
import { Route as AppTimelineRouteImport } from './routes/_app/timeline'
import { Route as AppTrashRouteImport } from './routes/_app/trash'
import { Route as AppVaultsRouteImport } from './routes/_app/vaults'

const AppRoute = AppRouteImport.update({
  id: '/_app',
  getParentRoute: () => rootRouteImport,
} as any)
const AppIndexRoute = AppIndexRouteImport.update({
  id: '/',
  path: '/',
  getParentRoute: () => AppRoute,
} as any)
const AppDevicesRoute = AppDevicesRouteImport.update({
  id: '/devices',
  path: '/devices',
  getParentRoute: () => AppRoute,
} as any)
const AppFavoritesRoute = AppFavoritesRouteImport.update({
  id: '/favorites',
  path: '/favorites',
  getParentRoute: () => AppRoute,
} as any)
const AppGalleryRoute = AppGalleryRouteImport.update({
  id: '/gallery',
  path: '/gallery',
  getParentRoute: () => AppRoute,
} as any)
const AppSearchRoute = AppSearchRouteImport.update({
  id: '/search',
  path: '/search',
  getParentRoute: () => AppRoute,
} as any)
const AppSettingsRoute = AppSettingsRouteImport.update({
  id: '/settings',
  path: '/settings',
  getParentRoute: () => AppRoute,
} as any)
const AppSyncRoute = AppSyncRouteImport.update({
  id: '/sync',
  path: '/sync',
  getParentRoute: () => AppRoute,
} as any)
const AppTimelineRoute = AppTimelineRouteImport.update({
  id: '/timeline',
  path: '/timeline',
  getParentRoute: () => AppRoute,
} as any)
const AppTrashRoute = AppTrashRouteImport.update({
  id: '/trash',
  path: '/trash',
  getParentRoute: () => AppRoute,
} as any)
const AppVaultsRoute = AppVaultsRouteImport.update({
  id: '/vaults',
  path: '/vaults',
  getParentRoute: () => AppRoute,
} as any)

export interface FileRoutesByFullPath {
  '/': typeof AppIndexRoute
  '/devices': typeof AppDevicesRoute
  '/favorites': typeof AppFavoritesRoute
  '/gallery': typeof AppGalleryRoute
  '/search': typeof AppSearchRoute
  '/settings': typeof AppSettingsRoute
  '/sync': typeof AppSyncRoute
  '/timeline': typeof AppTimelineRoute
  '/trash': typeof AppTrashRoute
  '/vaults': typeof AppVaultsRoute
}
export interface FileRoutesByTo {
  '/devices': typeof AppDevicesRoute
  '/favorites': typeof AppFavoritesRoute
  '/gallery': typeof AppGalleryRoute
  '/search': typeof AppSearchRoute
  '/settings': typeof AppSettingsRoute
  '/sync': typeof AppSyncRoute
  '/timeline': typeof AppTimelineRoute
  '/trash': typeof AppTrashRoute
  '/vaults': typeof AppVaultsRoute
  '/': typeof AppIndexRoute
}
export interface FileRoutesById {
  __root__: typeof rootRouteImport
  '/_app': typeof AppRouteWithChildren
  '/_app/devices': typeof AppDevicesRoute
  '/_app/favorites': typeof AppFavoritesRoute
  '/_app/gallery': typeof AppGalleryRoute
  '/_app/search': typeof AppSearchRoute
  '/_app/settings': typeof AppSettingsRoute
  '/_app/sync': typeof AppSyncRoute
  '/_app/timeline': typeof AppTimelineRoute
  '/_app/trash': typeof AppTrashRoute
  '/_app/vaults': typeof AppVaultsRoute
  '/_app/': typeof AppIndexRoute
}
export interface FileRouteTypes {
  fileRoutesByFullPath: FileRoutesByFullPath
  fullPaths:
    | '/'
    | '/devices'
    | '/favorites'
    | '/gallery'
    | '/search'
    | '/settings'
    | '/sync'
    | '/timeline'
    | '/trash'
    | '/vaults'
  fileRoutesByTo: FileRoutesByTo
  to:
    | '/devices'
    | '/favorites'
    | '/gallery'
    | '/search'
    | '/settings'
    | '/sync'
    | '/timeline'
    | '/trash'
    | '/vaults'
    | '/'
  id:
    | '__root__'
    | '/_app'
    | '/_app/devices'
    | '/_app/favorites'
    | '/_app/gallery'
    | '/_app/search'
    | '/_app/settings'
    | '/_app/sync'
    | '/_app/timeline'
    | '/_app/trash'
    | '/_app/vaults'
    | '/_app/'
  fileRoutesById: FileRoutesById
}
export interface RootRouteChildren {
  AppRoute: typeof AppRouteWithChildren
}

declare module '@tanstack/react-router' {
  interface FileRoutesByPath {
    '/_app': {
      id: '/_app'
      path: ''
      fullPath: '/'
      preLoaderRoute: typeof AppRouteImport
      parentRoute: typeof rootRouteImport
    }
    '/_app/': {
      id: '/_app/'
      path: '/'
      fullPath: '/'
      preLoaderRoute: typeof AppIndexRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/devices': {
      id: '/_app/devices'
      path: '/devices'
      fullPath: '/devices'
      preLoaderRoute: typeof AppDevicesRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/favorites': {
      id: '/_app/favorites'
      path: '/favorites'
      fullPath: '/favorites'
      preLoaderRoute: typeof AppFavoritesRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/gallery': {
      id: '/_app/gallery'
      path: '/gallery'
      fullPath: '/gallery'
      preLoaderRoute: typeof AppGalleryRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/search': {
      id: '/_app/search'
      path: '/search'
      fullPath: '/search'
      preLoaderRoute: typeof AppSearchRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/settings': {
      id: '/_app/settings'
      path: '/settings'
      fullPath: '/settings'
      preLoaderRoute: typeof AppSettingsRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/sync': {
      id: '/_app/sync'
      path: '/sync'
      fullPath: '/sync'
      preLoaderRoute: typeof AppSyncRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/timeline': {
      id: '/_app/timeline'
      path: '/timeline'
      fullPath: '/timeline'
      preLoaderRoute: typeof AppTimelineRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/trash': {
      id: '/_app/trash'
      path: '/trash'
      fullPath: '/trash'
      preLoaderRoute: typeof AppTrashRouteImport
      parentRoute: typeof AppRoute
    }
    '/_app/vaults': {
      id: '/_app/vaults'
      path: '/vaults'
      fullPath: '/vaults'
      preLoaderRoute: typeof AppVaultsRouteImport
      parentRoute: typeof AppRoute
    }
  }
}

interface AppRouteChildren {
  AppDevicesRoute: typeof AppDevicesRoute
  AppFavoritesRoute: typeof AppFavoritesRoute
  AppGalleryRoute: typeof AppGalleryRoute
  AppSearchRoute: typeof AppSearchRoute
  AppSettingsRoute: typeof AppSettingsRoute
  AppSyncRoute: typeof AppSyncRoute
  AppTimelineRoute: typeof AppTimelineRoute
  AppTrashRoute: typeof AppTrashRoute
  AppVaultsRoute: typeof AppVaultsRoute
  AppIndexRoute: typeof AppIndexRoute
}

const AppRouteChildren: AppRouteChildren = {
  AppDevicesRoute: AppDevicesRoute,
  AppFavoritesRoute: AppFavoritesRoute,
  AppGalleryRoute: AppGalleryRoute,
  AppSearchRoute: AppSearchRoute,
  AppSettingsRoute: AppSettingsRoute,
  AppSyncRoute: AppSyncRoute,
  AppTimelineRoute: AppTimelineRoute,
  AppTrashRoute: AppTrashRoute,
  AppVaultsRoute: AppVaultsRoute,
  AppIndexRoute: AppIndexRoute,
}

const AppRouteWithChildren = AppRoute._addFileChildren(AppRouteChildren)

const rootRouteChildren: RootRouteChildren = {
  AppRoute: AppRouteWithChildren,
}
export const routeTree = rootRouteImport
  ._addFileChildren(rootRouteChildren)
  ._addFileTypes<FileRouteTypes>()
MEOF_cb917d6ea6d1

echo '  src/styles/globals.css'
write_file "src/styles/globals.css" << 'MEOF_8da7f7cbf05a'
@import "tailwindcss";

/* ─────────────────────────────────────────
   OBSIDIAN ARCHIVE — Design System Tokens
   ───────────────────────────────────────── */

@theme {
  /* Palette — Zinc scale as neutral backbone */
  --color-surface-base:      #09090b;   /* zinc-950 */
  --color-surface-raised:    #111113;   /* slightly above base */
  --color-surface-overlay:   #18181b;   /* zinc-900 */
  --color-surface-subtle:    #27272a;   /* zinc-800 */
  --color-surface-muted:     #3f3f46;   /* zinc-700 */

  --color-border-default:    #27272a;   /* zinc-800 */
  --color-border-subtle:     #18181b;   /* zinc-900 */
  --color-border-bright:     #3f3f46;   /* zinc-700 */

  --color-text-primary:      #fafafa;   /* zinc-50 */
  --color-text-secondary:    #a1a1aa;   /* zinc-400 */
  --color-text-muted:        #71717a;   /* zinc-500 */
  --color-text-disabled:     #52525b;   /* zinc-600 */

  --color-accent:            #e4e4e7;   /* zinc-200 — the only "bright" */
  --color-accent-dim:        #a1a1aa;   /* zinc-400 */

  --color-danger:            #ef4444;   /* red-500 */
  --color-danger-surface:    #450a0a;   /* red-950 */
  --color-success:           #22c55e;   /* green-500 */
  --color-warning:           #f59e0b;   /* amber-500 */

  /* Typography */
  --font-sans:    system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --font-mono:    ui-monospace, "JetBrains Mono", "SF Mono", Menlo, monospace;

  /* Spacing base: 4px */
  --spacing-px:  1px;
  --spacing-0:   0;
  --spacing-1:   4px;
  --spacing-2:   8px;
  --spacing-3:   12px;
  --spacing-4:   16px;
  --spacing-5:   20px;
  --spacing-6:   24px;
  --spacing-8:   32px;
  --spacing-10:  40px;
  --spacing-12:  48px;
  --spacing-16:  64px;
  --spacing-20:  80px;
  --spacing-24:  96px;

  /* Radii */
  --radius-sm:   4px;
  --radius-md:   8px;
  --radius-lg:   12px;
  --radius-xl:   16px;
  --radius-2xl:  24px;
  --radius-full: 9999px;

  /* Shadows — elevation scale */
  --shadow-1:  0 1px 2px 0 rgb(0 0 0 / 0.4);
  --shadow-2:  0 2px 8px 0 rgb(0 0 0 / 0.5);
  --shadow-3:  0 8px 24px 0 rgb(0 0 0 / 0.6);
  --shadow-4:  0 24px 64px 0 rgb(0 0 0 / 0.7);

  /* Motion tokens */
  --duration-instant: 80ms;
  --duration-fast:    150ms;
  --duration-normal:  220ms;
  --duration-slow:    350ms;

  --ease-out:    cubic-bezier(0.0, 0.0, 0.2, 1.0);
  --ease-in:     cubic-bezier(0.4, 0.0, 1.0, 1.0);
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1.0);
  --ease-standard: cubic-bezier(0.2, 0.0, 0.0, 1.0);
}

/* ─── Base reset ─── */
*, *::before, *::after { box-sizing: border-box; }

html {
  font-optical-sizing: auto;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

body {
  background: var(--color-surface-base);
  color: var(--color-text-primary);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
  overflow-x: hidden;
}

/* ─── Scrollbar styling ─── */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb {
  background: var(--color-surface-muted);
  border-radius: var(--radius-full);
}
::-webkit-scrollbar-thumb:hover { background: var(--color-border-bright); }

/* ─── Focus ring ─── */
:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}

/* ─── Selection ─── */
::selection {
  background: var(--color-surface-muted);
  color: var(--color-text-primary);
}

/* ─── Global transitions (non-layout only) ─── */
@media (prefers-reduced-motion: no-preference) {
  .motion-safe-transition {
    transition: opacity var(--duration-normal) var(--ease-out),
                transform var(--duration-normal) var(--ease-out);
  }
}
@media (prefers-reduced-motion: reduce) {
  .motion-safe-transition {
    transition: opacity var(--duration-fast) linear;
  }
}

/* ─── Image loading ─── */
img {
  display: block;
  max-width: 100%;
}

/* ─── Utility: hide scrollbar but keep scroll ─── */
.scrollbar-none {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-none::-webkit-scrollbar { display: none; }

/* ─── Thumbnail skeleton ─── */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-surface-overlay) 25%,
    var(--color-surface-subtle) 50%,
    var(--color-surface-overlay) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

/* ─── Photo grid hover overlay ─── */
.photo-overlay {
  transition: opacity var(--duration-fast) var(--ease-out);
}

MEOF_8da7f7cbf05a

echo '  src/types/index.ts'
write_file "src/types/index.ts" << 'MEOF_6c58ae6ccad7'
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

MEOF_6c58ae6ccad7

echo '  src/lib/utils.ts'
write_file "src/lib/utils.ts" << 'MEOF_1f35ff4e17df'
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { Media } from '@/types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export function formatMonthYear(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

export function formatDate(date: Date): string {
  return date.toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric' })
}

export function formatRelative(date: Date): string {
  const now = Date.now()
  const diff = now - date.getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return formatDate(date)
}

export function mediaDate(takenAt: string | null, uploadedAt: string): Date {
  return takenAt ? new Date(takenAt) : new Date(uploadedAt)
}

export function groupByMonth(
  items: Media[],
): Array<{ label: string; date: Date; items: Media[] }> {
  const groups = new Map<string, { label: string; date: Date; items: Media[] }>()
  for (const item of items) {
    const date = mediaDate(item.TakenAt, item.UploadedAt)
    const key = `${date.getFullYear()}-${date.getMonth()}`
    if (!groups.has(key)) {
      groups.set(key, { label: formatMonthYear(date), date, items: [] })
    }
    groups.get(key)!.items.push(item)
  }
  return Array.from(groups.values()).sort((a, b) => b.date.getTime() - a.date.getTime())
}

export function isVideo(mime: string): boolean {
  return mime.startsWith('video/')
}

export function isImage(mime: string): boolean {
  return mime.startsWith('image/')
}

export async function hashFile(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}

MEOF_1f35ff4e17df

echo '  src/stores/auth.ts'
write_file "src/stores/auth.ts" << 'MEOF_054ef27e12b4'
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { AuthSession } from '@/types'

interface AuthStore {
  session: AuthSession | null
  setSession: (session: AuthSession) => void
  clearSession: () => void
  isAuthenticated: boolean
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      session: null,
      isAuthenticated: false,
      setSession: (session) => {
        set({ session, isAuthenticated: true })
      },
      clearSession: () => {
        set({ session: null, isAuthenticated: false })
        localStorage.removeItem('mnemos_session')
      },
    }),
    {
      name: 'mnemos_session',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state) => {
        if (state?.session) state.isAuthenticated = true
      },
    },
  ),
)

MEOF_054ef27e12b4

echo '  src/stores/ui.ts'
write_file "src/stores/ui.ts" << 'MEOF_b9f33823aa79'
import { create } from 'zustand'
import type { GalleryViewMode } from '@/types'

interface UIStore {
  // Viewer
  viewerMediaId: string | null
  openViewer: (id: string) => void
  closeViewer: () => void

  // Gallery selection
  selectedIds: Set<string>
  isSelectMode: boolean
  toggleSelectMode: () => void
  toggleSelect: (id: string) => void
  selectAll: (ids: string[]) => void
  clearSelection: () => void

  // Gallery view
  viewMode: GalleryViewMode
  setViewMode: (mode: GalleryViewMode) => void

  // Toast
  toasts: Toast[]
  addToast: (toast: Omit<Toast, 'id'>) => void
  removeToast: (id: string) => void
}

export interface Toast {
  id: string
  message: string
  type: 'success' | 'error' | 'info'
}

let toastCounter = 0

export const useUIStore = create<UIStore>()((set) => ({
  // Viewer
  viewerMediaId: null,
  openViewer: (id) => set({ viewerMediaId: id }),
  closeViewer: () => set({ viewerMediaId: null }),

  // Selection
  selectedIds: new Set(),
  isSelectMode: false,
  toggleSelectMode: () =>
    set((s) => ({
      isSelectMode: !s.isSelectMode,
      selectedIds: new Set(),
    })),
  toggleSelect: (id) =>
    set((s) => {
      const next = new Set(s.selectedIds)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return { selectedIds: next }
    }),
  selectAll: (ids) => set({ selectedIds: new Set(ids) }),
  clearSelection: () => set({ selectedIds: new Set(), isSelectMode: false }),

  // View mode
  viewMode: 'timeline',
  setViewMode: (mode) => set({ viewMode: mode }),

  // Toasts
  toasts: [],
  addToast: (toast) => {
    const id = String(++toastCounter)
    set((s) => ({ toasts: [...s.toasts, { ...toast, id }] }))
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
    }, 3500)
  },
  removeToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))

MEOF_b9f33823aa79

echo '  src/main.tsx'
write_file "src/main.tsx" << 'MEOF_02094b933dd3'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { routeTree } from './routeTree.gen'
import '@/styles/globals.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error && typeof error === 'object' && 'status' in error) {
          const status = (error as { status: number }).status
          if (status === 401 || status === 404) return false
        }
        return failureCount < 2
      },
      staleTime: 30_000,
      gcTime: 5 * 60_000,
    },
    mutations: { retry: false },
  },
})

const router = createRouter({
  routeTree,
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0,
})

declare module '@tanstack/react-router' {
  interface Register { router: typeof router }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)

MEOF_02094b933dd3

rm -f src/api/clientHelpers.ts

echo ''
echo '✓ Done. NOW DO: Ctrl+C pnpm dev → pnpm dev'
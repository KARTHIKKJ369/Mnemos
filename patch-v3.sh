#!/usr/bin/env bash
# Mnemos patch v3 — fix 401 + folder picker fallback
# Run from inside mnemos-web/
set -e

write_file() { local p="$1"; mkdir -p "$(dirname "$p")"; cat > "$p"; }

echo '→ Applying patch v3...'

echo '  src/api/client.ts'
write_file "src/api/client.ts" << 'P3_8789fc09c7'
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
P3_8789fc09c7

echo '  src/hooks/useStorage.ts'
write_file "src/hooks/useStorage.ts" << 'P3_c47b308385'
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
P3_c47b308385

echo '  src/features/storage/FirstRunWizard.tsx'
write_file "src/features/storage/FirstRunWizard.tsx" << 'P3_cf25434fa0'
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
P3_cf25434fa0

echo ''
echo '✓ Patch v3 done. Vite auto-reloads.'

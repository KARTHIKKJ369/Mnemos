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

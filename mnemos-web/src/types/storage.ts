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

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

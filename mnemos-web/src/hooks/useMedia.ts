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
  restoreMedia,
  permanentDeleteMedia,
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
      // Optimistic update across all media lists/infinite queries
      queryClient.setQueriesData<InfiniteData<MediaSearchResponse>>(
        { queryKey: mediaKeys.all },
        (old) => {
          if (!old || !old.pages) return old
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
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.all })
    },
  })
}

export function useDeleteMedia() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteMedia(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.all })
    },
  })
}

export function useRestoreMedia() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => restoreMedia(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.all })
    },
  })
}

export function usePermanentDeleteMedia() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => permanentDeleteMedia(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mediaKeys.all })
    },
  })
}


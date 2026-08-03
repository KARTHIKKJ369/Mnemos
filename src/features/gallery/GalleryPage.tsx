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


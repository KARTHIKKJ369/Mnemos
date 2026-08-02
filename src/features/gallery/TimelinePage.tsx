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
          overscan={400}
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

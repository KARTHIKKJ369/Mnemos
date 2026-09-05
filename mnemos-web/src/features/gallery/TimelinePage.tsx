import { useState, useMemo } from 'react'
import { Virtuoso } from 'react-virtuoso'
import { Clock, Calendar, CalendarDays } from 'lucide-react'
import { useMediaInfinite } from '@/hooks/useMedia'
import { groupByDay, groupByMonth } from '@/lib/utils'
import { PhotoTile } from './PhotoTile'
import { MediaViewer } from '@/features/viewer/MediaViewer'
import type { Media } from '@/types'

type GroupMode = 'day' | 'month'

type Row =
  | { type: 'header'; label: string; subLabel?: string; count: number }
  | { type: 'items'; items: Media[] }

const COLS = 5
const GAP = 8

function buildRows(
  groups: Array<{ label: string; subLabel?: string; count?: number; items: Media[] }>,
  cols: number,
): Row[] {
  const rows: Row[] = []
  for (const g of groups) {
    rows.push({
      type: 'header',
      label: g.label,
      subLabel: g.subLabel,
      count: g.items.length,
    })
    for (let i = 0; i < g.items.length; i += cols) {
      rows.push({ type: 'items', items: g.items.slice(i, i + cols) })
    }
  }
  return rows
}

export function TimelinePage() {
  const [groupMode, setGroupMode] = useState<GroupMode>('day')

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } =
    useMediaInfinite({ sort: 'taken_at', order: 'desc' })

  const allMedia = useMemo(
    () => data?.pages.flatMap((p) => p.media) ?? [],
    [data],
  )
  const allIds = useMemo(() => allMedia.map((m) => m.FileID), [allMedia])

  const groups = useMemo(() => {
    return groupMode === 'day' ? groupByDay(allMedia) : groupByMonth(allMedia)
  }, [allMedia, groupMode])

  const rows = useMemo(() => buildRows(groups, COLS), [groups])

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* ── Sticky Top Header ── */}
      <div className="flex items-center justify-between px-6 py-3.5 border-b border-[--color-border-subtle] bg-[--color-surface-base]/85 backdrop-blur-md sticky top-0 z-20 shadow-xs">
        <div className="flex items-center gap-2.5">
          <Clock size={16} className="text-[--color-accent]" />
          <h1 className="text-base font-bold text-[--color-text-primary] tracking-tight">Timeline</h1>
          {allMedia.length > 0 && (
            <span className="text-xs text-[--color-text-muted] font-mono bg-[--color-surface-subtle] px-2 py-0.5 rounded-full border border-[--color-border-subtle]">
              {allMedia.length.toLocaleString()} items
            </span>
          )}
        </div>

        {/* Day / Month Grouping Toggle */}
        <div className="flex items-center gap-1 bg-[--color-surface-subtle] p-0.5 rounded-[--radius-md] border border-[--color-border-subtle]">
          <button
            onClick={() => setGroupMode('day')}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium cursor-pointer transition-all ${
              groupMode === 'day'
                ? 'bg-white text-black font-semibold shadow-xs'
                : 'text-[--color-text-secondary] hover:text-[--color-text-primary]'
            }`}
          >
            <CalendarDays size={12} />
            <span>Days</span>
          </button>
          <button
            onClick={() => setGroupMode('month')}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium cursor-pointer transition-all ${
              groupMode === 'month'
                ? 'bg-white text-black font-semibold shadow-xs'
                : 'text-[--color-text-secondary] hover:text-[--color-text-primary]'
            }`}
          >
            <Calendar size={12} />
            <span>Months</span>
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex-1 p-6 space-y-6">
          {[1, 2].map((i) => (
            <div key={i}>
              <div className="h-4 w-40 skeleton rounded mb-3" />
              <div
                className="grid gap-2"
                style={{ gridTemplateColumns: `repeat(${COLS}, minmax(0, 1fr))` }}
              >
                {Array.from({ length: COLS * 2 }).map((_, j) => (
                  <div key={j} className="aspect-square skeleton rounded-[--radius-md]" />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : allMedia.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-full gap-3 text-center p-12">
          <div className="w-14 h-14 rounded-2xl bg-[--color-surface-overlay] flex items-center justify-center border border-[--color-border-subtle]">
            <Clock size={24} className="text-[--color-text-disabled]" />
          </div>
          <div className="space-y-1 max-w-xs">
            <h3 className="text-sm font-semibold text-[--color-text-primary]">No timeline media</h3>
            <p className="text-xs text-[--color-text-muted]">
              Upload photos with EXIF date metadata to build your visual timeline.
            </p>
          </div>
        </div>
      ) : (
        <Virtuoso
          className="flex-1"
          totalCount={rows.length}
          overscan={500}
          endReached={() => {
            if (hasNextPage && !isFetchingNextPage) fetchNextPage()
          }}
          itemContent={(index) => {
            const row = rows[index]
            if (!row) return null
            if (row.type === 'header') {
              return (
                <div className="px-6 pt-6 pb-2 sticky top-0 z-10 bg-[--color-surface-base]/95 backdrop-blur-sm border-b border-transparent">
                  <div className="flex items-baseline gap-2.5">
                    <h2 className="text-sm font-bold text-[--color-text-primary] tracking-tight">
                      {row.label}
                    </h2>
                    {row.subLabel && row.subLabel !== row.label && (
                      <span className="text-xs text-[--color-text-muted] font-medium">
                        · {row.subLabel}
                      </span>
                    )}
                    <span className="text-[11px] font-mono text-[--color-text-muted] bg-[--color-surface-subtle] px-1.5 py-0.2 rounded-full border border-[--color-border-subtle]">
                      {row.count}
                    </span>
                  </div>
                </div>
              )
            }
            return (
              <div
                className="grid px-6 pb-2"
                style={{
                  gridTemplateColumns: `repeat(${COLS}, minmax(0, 1fr))`,
                  gap: `${GAP}px`,
                }}
              >
                {row.items.map((media) => (
                  <PhotoTile key={media.FileID} media={media} />
                ))}
              </div>
            )
          }}
          components={{
            Footer: isFetchingNextPage
              ? () => (
                  <div className="flex justify-center py-6">
                    <span className="animate-spin h-5 w-5 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
                  </div>
                )
              : undefined,
          }}
        />
      )}

      {/* ── MediaViewer for Timeline preview clicks ── */}
      <MediaViewer allIds={allIds} />
    </div>
  )
}



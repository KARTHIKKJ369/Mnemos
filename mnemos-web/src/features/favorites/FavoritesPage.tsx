import { useMemo } from 'react'
import { Heart } from 'lucide-react'
import { useMediaSearch } from '@/hooks/useMedia'
import { PhotoTile } from '@/features/gallery/PhotoTile'
import { MediaViewer } from '@/features/viewer/MediaViewer'

export function FavoritesPage() {
  const { data, isLoading } = useMediaSearch({ favorite: true, sort: 'taken_at', order: 'desc', limit: 300 })
  const items = data?.media ?? []
  const allIds = useMemo(() => items.map((m) => m.FileID), [items])

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      <div className="flex items-center gap-2.5 px-6 py-3.5 border-b border-[--color-border-subtle] bg-[--color-surface-base]/85 backdrop-blur-md sticky top-0 z-20 shadow-xs">
        <Heart size={16} className="text-rose-500 fill-rose-500" />
        <h1 className="text-base font-bold text-[--color-text-primary] tracking-tight">Favorites</h1>
        {items.length > 0 && (
          <span className="text-xs text-[--color-text-muted] font-mono bg-[--color-surface-subtle] px-2 py-0.5 rounded-full border border-[--color-border-subtle]">
            {items.length} items
          </span>
        )}
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid gap-2 grid-cols-3 sm:grid-cols-4 md:grid-cols-5">
            {Array.from({ length: 15 }).map((_, i) => (
              <div key={i} className="aspect-square bg-[--color-surface-subtle] rounded-[--radius-md] animate-pulse" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-80 gap-3 text-center">
            <div className="w-14 h-14 rounded-2xl bg-[--color-surface-overlay] flex items-center justify-center border border-[--color-border-subtle]">
              <Heart size={24} className="text-[--color-text-disabled]" />
            </div>
            <div className="space-y-1 max-w-xs">
              <h3 className="text-sm font-semibold text-[--color-text-primary]">No favorites yet</h3>
              <p className="text-xs text-[--color-text-muted]">
                Hover any photo or video and click the heart to save your favorite memories here.
              </p>
            </div>
          </div>
        ) : (
          <div className="grid gap-2 grid-cols-3 sm:grid-cols-4 md:grid-cols-5">
            {items.map((media) => (
              <PhotoTile key={media.FileID} media={media} />
            ))}
          </div>
        )}
      </div>

      <MediaViewer allIds={allIds} />
    </div>
  )
}



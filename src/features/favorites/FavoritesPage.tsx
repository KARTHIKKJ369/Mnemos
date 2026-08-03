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


import { useState, useDeferredValue } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { Search, X, Image, Video, Star } from 'lucide-react'
import { useMediaSearch } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { AuthImage } from '@/components/shared/AuthImage'
import { MediaViewer } from '@/features/viewer/MediaViewer'
import { cn } from '@/lib/utils'
import type { MediaSearchParams } from '@/types'

type FilterKey = 'all' | 'images' | 'videos' | 'favorites'

const FILTERS: { key: FilterKey; label: string; icon: React.ReactNode }[] = [
  { key: 'all', label: 'All', icon: null },
  { key: 'images', label: 'Photos', icon: <Image size={12} /> },
  { key: 'videos', label: 'Videos', icon: <Video size={12} /> },
  { key: 'favorites', label: 'Favorites', icon: <Star size={12} /> },
]

function buildParams(query: string, filter: FilterKey): MediaSearchParams {
  const params: MediaSearchParams = { sort: 'uploaded_at', order: 'desc' }
  if (query) params.query = query
  if (filter === 'images') params.mime_type = 'image/'
  if (filter === 'videos') params.mime_type = 'video/'
  if (filter === 'favorites') params.favorite = true
  return params
}

export function SearchPage() {
  const [rawQuery, setRawQuery] = useState('')
  const [filter, setFilter] = useState<FilterKey>('all')
  const { openViewer } = useUIStore()

  const query = useDeferredValue(rawQuery)
  const params = buildParams(query, filter)

  const { data, isLoading, isFetching } = useMediaSearch(params)
  const results = data?.media ?? []
  const allIds = results.map((m) => m.FileID)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-[--color-border-subtle] space-y-3">
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <Input
              value={rawQuery}
              onChange={(e) => setRawQuery(e.target.value)}
              placeholder="Search photos, videos, filenames…"
              leftIcon={
                isFetching
                  ? <span className="animate-spin h-3 w-3 border-2 border-current border-t-transparent rounded-full" />
                  : <Search size={13} />
              }
              autoFocus
            />
          </div>
          {rawQuery && (
            <Button size="icon" variant="ghost" onClick={() => setRawQuery('')} aria-label="Clear">
              <X size={14} />
            </Button>
          )}
        </div>

        {/* Filter chips */}
        <div className="flex items-center gap-1">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              onClick={() => setFilter(f.key)}
              className={cn(
                'flex items-center gap-1.5 px-3 h-6 rounded-full text-xs font-medium',
                'transition-colors duration-[120ms]',
                filter === f.key
                  ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
                  : 'text-[--color-text-muted] hover:bg-[--color-surface-overlay] hover:text-[--color-text-secondary]',
              )}
            >
              {f.icon}
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Results */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid gap-1" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
            {Array.from({ length: 15 }).map((_, i) => (
              <div key={i} className="skeleton aspect-square rounded-sm" />
            ))}
          </div>
        ) : results.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
            <Search size={28} className="text-[--color-text-disabled]" />
            <div>
              <p className="text-sm text-[--color-text-secondary]">
                {rawQuery ? `No results for "${rawQuery}"` : 'Start typing to search'}
              </p>
              <p className="text-xs text-[--color-text-muted] mt-1">
                Search by filename, type, or use date filters
              </p>
            </div>
          </div>
        ) : (
          <>
            <p className="text-xs text-[--color-text-muted] mb-3">
              {results.length} result{results.length > 1 ? 's' : ''}
            </p>
            <AnimatePresence mode="wait">
              <motion.div
                key={`${query}-${filter}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.12 }}
                className="grid gap-1"
                style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}
              >
                {results.map((media) => (
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
                      <div className="absolute inset-0 bg-[--color-surface-overlay] flex items-center justify-center">
                        <span className="text-[10px] text-[--color-text-disabled]">
                          {media.Extension.toUpperCase()}
                        </span>
                      </div>
                    )}
                    <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </button>
                ))}
              </motion.div>
            </AnimatePresence>
          </>
        )}
      </div>

      <MediaViewer allIds={allIds} />
    </div>
  )
}


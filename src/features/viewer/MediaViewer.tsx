import { useEffect, useCallback, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  X, ChevronLeft, ChevronRight, Heart, Trash2, 
  Info, 
} from 'lucide-react'
import { useMediaDetail, useFavoriteMedia, useDeleteMedia, useMediaBlob } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { formatBytes, formatDate, isVideo } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { Media } from '@/types'

// ─── Metadata panel ───────────────────────────────────────────────────────────

function MetadataRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <span className="text-xs text-[--color-text-muted] w-20 flex-shrink-0 pt-0.5">{label}</span>
      <span className="text-xs text-[--color-text-secondary] leading-relaxed">{value}</span>
    </div>
  )
}

function MetadataPanel({ media }: { media: Media }) {
  return (
    <div className="w-64 flex-shrink-0 border-l border-[--color-border-subtle] bg-[--color-surface-raised] p-5 overflow-y-auto">
      <h3 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest mb-4">
        Info
      </h3>
      <div className="space-y-3">
        <MetadataRow label="Filename" value={media.Filename} />
        {media.TakenAt && (
          <MetadataRow
            label="Taken"
            value={formatDate(new Date(media.TakenAt))}
          />
        )}
        <MetadataRow label="Size" value={formatBytes(media.SizeBytes)} />
        <MetadataRow label="Type" value={media.MIMEType} />
        {media.Width && media.Height && (
          <MetadataRow label="Dimensions" value={`${media.Width} × ${media.Height}`} />
        )}
        {media.DurationMS && (
          <MetadataRow
            label="Duration"
            value={`${Math.floor(media.DurationMS / 1000)}s`}
          />
        )}
        {(media.CameraMake ?? media.CameraModel) && (
          <MetadataRow
            label="Camera"
            value={[media.CameraMake, media.CameraModel].filter(Boolean).join(' ')}
          />
        )}
        {media.GPSLat !== null && media.GPSLon !== null && (
          <MetadataRow
            label="Location"
            value={`${media.GPSLat.toFixed(4)}, ${media.GPSLon.toFixed(4)}`}
          />
        )}
        <MetadataRow label="Hash" value={media.Hash.slice(0, 16) + '…'} />
      </div>
    </div>
  )
}

// ─── Viewer image ─────────────────────────────────────────────────────────────

function ViewerImage({ media }: { media: Media }) {
  const { data: src, isLoading } = useMediaBlob(
    media.FileID,
    media.PreviewAvailable ? 'preview' : 'original',
  )

  const video = isVideo(media.MIMEType)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center w-full h-full">
        <span className="animate-spin h-6 w-6 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!src) return null

  if (video) {
    return (
      <video
        src={src}
        controls
        autoPlay
        className="max-w-full max-h-full rounded-[--radius-md] object-contain"
        style={{ maxWidth: 'calc(100vw - 96px)', maxHeight: 'calc(100vh - 80px)' }}
      />
    )
  }

  return (
    <motion.img
      key={src}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.18 }}
      src={src}
      alt={media.Filename}
      className="max-w-full max-h-full object-contain rounded-sm select-none"
      style={{ maxWidth: 'calc(100vw - 64px)', maxHeight: 'calc(100vh - 80px)' }}
      draggable={false}
    />
  )
}

// ─── Main viewer ─────────────────────────────────────────────────────────────

interface MediaViewerProps {
  allIds?: string[]
}

export function MediaViewer({ allIds = [] }: MediaViewerProps) {
  const { viewerMediaId, closeViewer, openViewer, addToast } = useUIStore()
  const { data: media } = useMediaDetail(viewerMediaId)
  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()
  const [showInfo, setShowInfo] = useState(false)

  const currentIndex = viewerMediaId ? allIds.indexOf(viewerMediaId) : -1

  const goNext = useCallback(() => {
    if (currentIndex < allIds.length - 1) openViewer(allIds[currentIndex + 1])
  }, [currentIndex, allIds, openViewer])

  const goPrev = useCallback(() => {
    if (currentIndex > 0) openViewer(allIds[currentIndex - 1])
  }, [currentIndex, allIds, openViewer])

  // Keyboard navigation
  useEffect(() => {
    if (!viewerMediaId) return
    const handleKey = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'Escape': closeViewer(); break
        case 'ArrowRight': goNext(); break
        case 'ArrowLeft': goPrev(); break
        case 'i': setShowInfo((s) => !s); break
      }
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [viewerMediaId, closeViewer, goNext, goPrev])

  const handleFavorite = async () => {
    if (!media) return
    await favoriteMedia.mutateAsync({ id: media.FileID, favorite: !media.Favorite })
    addToast({ type: 'success', message: media.Favorite ? 'Removed from favorites' : 'Added to favorites' })
  }

  const handleDelete = async () => {
    if (!media) return
    await deleteMedia.mutateAsync(media.FileID)
    addToast({ type: 'success', message: 'Moved to trash' })
    closeViewer()
  }

  return (
    <AnimatePresence>
      {viewerMediaId && (
        <motion.div
          key="viewer"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          className="fixed inset-0 z-50 flex flex-col bg-black/95"
          onClick={(e) => { if (e.target === e.currentTarget) closeViewer() }}
        >
          {/* Toolbar */}
          <div className="flex items-center justify-between px-4 h-12 flex-shrink-0">
            <Button size="icon" variant="ghost" onClick={closeViewer} aria-label="Close">
              <X size={16} />
            </Button>

            <span className="text-xs text-[--color-text-muted]">
              {media?.Filename}
            </span>

            <div className="flex items-center gap-1">
              <Button
                size="icon"
                variant="ghost"
                onClick={handleFavorite}
                aria-label={media?.Favorite ? 'Unfavorite' : 'Favorite'}
              >
                <Heart
                  size={15}
                  className={media?.Favorite ? 'fill-[--color-text-primary] text-[--color-text-primary]' : ''}
                />
              </Button>
              <Button size="icon" variant="ghost" onClick={() => setShowInfo((s) => !s)} aria-label="Info">
                <Info size={15} />
              </Button>
              <Button size="icon" variant="ghost" onClick={handleDelete} aria-label="Delete">
                <Trash2 size={15} className="text-[--color-danger]" />
              </Button>
            </div>
          </div>

          {/* Content area */}
          <div className="flex flex-1 overflow-hidden">
            {/* Prev arrow */}
            <div className="flex items-center px-3">
              {currentIndex > 0 && (
                <Button size="icon" variant="ghost" onClick={goPrev} aria-label="Previous">
                  <ChevronLeft size={18} />
                </Button>
              )}
            </div>

            {/* Image */}
            <div className="flex-1 flex items-center justify-center overflow-hidden">
              {media && <ViewerImage media={media} />}
            </div>

            {/* Next arrow */}
            <div className="flex items-center px-3">
              {currentIndex < allIds.length - 1 && (
                <Button size="icon" variant="ghost" onClick={goNext} aria-label="Next">
                  <ChevronRight size={18} />
                </Button>
              )}
            </div>

            {/* Metadata panel */}
            <AnimatePresence>
              {showInfo && media && (
                <motion.div
                  key="info"
                  initial={{ opacity: 0, x: 16 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 16 }}
                  transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                >
                  <MetadataPanel media={media} />
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Counter */}
          {allIds.length > 1 && (
            <div className="text-center py-3">
              <span className="text-xs text-[--color-text-disabled] tabular-nums">
                {currentIndex + 1} / {allIds.length}
              </span>
            </div>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  )
}


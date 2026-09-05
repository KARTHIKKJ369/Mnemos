import { useState, useCallback } from 'react'
import { motion } from 'motion/react'
import { Heart, Play, Download, Trash2, Smartphone, Laptop, Globe } from 'lucide-react'
import { useUIStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useFavoriteMedia, useDeleteMedia } from '@/hooks/useMedia'
import { downloadMedia, getMediaURL } from '@/api/client'
import { isVideo, cn } from '@/lib/utils'
import type { Media } from '@/types'

interface PhotoTileProps {
  media: Media
}

function getDeviceIcon(type?: string) {
  switch (type) {
    case 'ios':
    case 'android':
      return <Smartphone size={10} />
    case 'mac':
      return <Laptop size={10} />
    default:
      return <Globe size={10} />
  }
}

/** Thumbnail for images: direct streaming URL with lazy loading, no blob buffering */
function ImageThumbnail({ media, className }: { media: Media; className: string }) {
  const [loaded, setLoaded] = useState(false)
  const type = media.ThumbnailAvailable ? 'thumbnail' : media.PreviewAvailable ? 'preview' : 'original'
  const src = getMediaURL(media.FileID, type)
  return (
    <>
      {!loaded && <div className={cn('skeleton', className)} />}
      <img
        src={src}
        alt={media.Filename}
        className={cn(
          'absolute inset-0 w-full h-full object-cover transition-all duration-300 group-hover:scale-105',
          loaded ? 'opacity-100' : 'opacity-0 pointer-events-none',
          className,
        )}
        loading="lazy"
        decoding="async"
        fetchPriority="low"
        onLoad={() => setLoaded(true)}
      />
    </>
  )
}

/** Thumbnail for videos:
 *  - If thumbnail was generated, show it as an <img>.
 *  - Otherwise show the first frame via <video preload="metadata" #t=0.5>.
 *  This avoids buffering the full video in JavaScript memory.
 */
function VideoThumbnail({ media, className }: { media: Media; className: string }) {
  const [loaded, setLoaded] = useState(false)

  if (media.ThumbnailAvailable) {
    const src = getMediaURL(media.FileID, 'thumbnail')
    return (
      <>
        {!loaded && <div className={cn('skeleton', className)} />}
        <img
          src={src}
          alt={media.Filename}
          className={cn(
            'absolute inset-0 w-full h-full object-cover transition-all duration-300 group-hover:scale-105',
            loaded ? 'opacity-100' : 'opacity-0 pointer-events-none',
            className,
          )}
          loading="lazy"
          decoding="async"
          fetchPriority="low"
          onLoad={() => setLoaded(true)}
        />
      </>
    )
  }

  // No thumbnail yet – use the video poster frame approach
  // We only request metadata (first few KB), not the entire video.
  const videoSrc = `${getMediaURL(media.FileID, 'original')}#t=0.5`
  return (
    <>
      {!loaded && <div className={cn('skeleton', className)} />}
      <video
        src={videoSrc}
        preload="metadata"
        muted
        playsInline
        className={cn(
          'absolute inset-0 w-full h-full object-cover transition-all duration-300 group-hover:scale-105',
          loaded ? 'opacity-100' : 'opacity-0 pointer-events-none',
          className,
        )}
        onLoadedMetadata={() => setLoaded(true)}
      />
    </>
  )
}

export function PhotoTile({ media }: PhotoTileProps) {
  const { session } = useAuthStore()
  const { openViewer, isSelectMode, selectedIds, toggleSelect, addToast } = useUIStore()
  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()
  const [isHovered, setIsHovered] = useState(false)

  const isSelected = selectedIds.has(media.FileID)
  const video = isVideo(media.MIMEType)
  const isFromThisDevice = session?.deviceId && media.UploadedByDeviceID === session.deviceId

  const handleClick = useCallback(() => {
    if (isSelectMode) {
      toggleSelect(media.FileID)
      return
    }
    openViewer(media.FileID)
  }, [isSelectMode, media.FileID, toggleSelect, openViewer])

  const handleFavorite = useCallback(
    async (e: React.MouseEvent) => {
      e.stopPropagation()
      await favoriteMedia.mutateAsync({ id: media.FileID, favorite: !media.Favorite })
      addToast({
        type: 'success',
        message: media.Favorite ? 'Removed from favorites' : 'Added to favorites',
      })
    },
    [media.FileID, media.Favorite, favoriteMedia, addToast],
  )

  const handleDownload = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      downloadMedia(media.FileID, media.Filename)
      addToast({ type: 'info', message: `Downloading ${media.Filename}` })
    },
    [media.FileID, media.Filename, addToast],
  )

  const handleDelete = useCallback(
    async (e: React.MouseEvent) => {
      e.stopPropagation()
      try {
        await deleteMedia.mutateAsync(media.FileID)
        addToast({ type: 'success', message: 'Moved to trash' })
      } catch {
        addToast({ type: 'error', message: 'Failed to delete photo' })
      }
    },
    [media.FileID, deleteMedia, addToast],
  )

  return (
    <motion.div
      className={cn(
        'relative aspect-square overflow-hidden rounded-[--radius-md] cursor-pointer',
        'select-none group border border-[--color-border-subtle]',
        isSelected && 'ring-2 ring-[--color-accent] ring-inset',
      )}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      whileTap={{ scale: 0.98 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.12 }}
    >
      {/* Thumbnail — images use <img>, videos use <video> to avoid blob buffering */}
      {video ? (
        <VideoThumbnail media={media} className="absolute inset-0 w-full h-full" />
      ) : (
        <ImageThumbnail media={media} className="absolute inset-0 w-full h-full" />
      )}

      {/* Hover Overlay */}
      <div
        className={cn(
          'absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-black/60 transition-opacity duration-150',
          isHovered || isSelected ? 'opacity-100' : 'opacity-0',
        )}
      />

      {/* Owner Badge */}
      <div className="absolute bottom-2 left-2 flex items-center gap-1.5 bg-black/60 backdrop-blur-xs px-2 py-0.5 rounded-full text-[10px] text-white/90 shadow-xs max-w-[calc(100%-48px)] truncate">
        {getDeviceIcon(media.UploadedByDeviceType)}
        <span className="truncate">
          {isFromThisDevice ? 'This device' : (media.UploadedByDeviceName || 'Other device')}
        </span>
      </div>

      {/* Video Indicator */}
      {video && (
        <div className="absolute top-2 right-2 bg-black/60 backdrop-blur-xs rounded-full p-1 text-white shadow-xs">
          <Play size={10} className="fill-white" />
        </div>
      )}

      {/* Favorite Heart */}
      {(media.Favorite || isHovered) && !isSelectMode && (
        <button
          onClick={handleFavorite}
          className={cn(
            'absolute top-2 right-2 p-1.5 rounded-full transition-all',
            'bg-black/40 hover:bg-black/70 text-white',
            video && 'mr-7',
          )}
          title={media.Favorite ? 'Remove from favorites' : 'Favorite'}
        >
          <Heart
            size={13}
            className={cn(media.Favorite ? 'text-rose-500 fill-rose-500' : 'text-white fill-transparent')}
          />
        </button>
      )}

      {/* Action Buttons (Hover) */}
      {isHovered && !isSelectMode && (
        <div className="absolute bottom-2 right-2 flex items-center gap-1.5">
          <button
            onClick={handleDownload}
            className="p-1.5 rounded-full bg-black/60 hover:bg-[--color-accent] hover:text-black text-white transition-colors shadow-xs"
            title="Download original file"
          >
            <Download size={13} />
          </button>
          <button
            onClick={handleDelete}
            className="p-1.5 rounded-full bg-black/60 hover:bg-rose-600 text-white transition-colors shadow-xs"
            title="Move to trash"
          >
            <Trash2 size={13} />
          </button>
        </div>
      )}

      {/* Multi-selection Checkbox */}
      {(isSelectMode || isHovered) && (
        <div
          className="absolute top-2 left-2 cursor-pointer"
          onClick={(e) => {
            e.stopPropagation()
            toggleSelect(media.FileID)
          }}
        >
          <div
            className={cn(
              'w-5 h-5 rounded-full border-2 transition-all flex items-center justify-center',
              isSelected
                ? 'bg-[--color-accent] border-[--color-accent]'
                : 'bg-black/40 border-white/70 hover:border-white',
            )}
          >
            {isSelected && (
              <svg width="10" height="10" viewBox="0 0 10 10" className="text-black" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="1.5,5 4,7.5 8.5,2.5" />
              </svg>
            )}
          </div>
        </div>
      )}
    </motion.div>
  )
}

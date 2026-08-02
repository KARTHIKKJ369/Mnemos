import { useState, useCallback } from 'react'
import { motion } from 'motion/react'
import { Heart, Play, CheckCircle2 } from 'lucide-react'
import { AuthImage } from '@/components/shared/AuthImage'
import { useUIStore } from '@/stores/ui'
import { useFavoriteMedia } from '@/hooks/useMedia'
import { isVideo } from '@/lib/utils'
import { cn } from '@/lib/utils'
import type { Media } from '@/types'

interface PhotoTileProps {
  media: Media
}

export function PhotoTile({ media }: PhotoTileProps) {
  const { openViewer, isSelectMode, selectedIds, toggleSelect, addToast } = useUIStore()
  const favoriteMedia = useFavoriteMedia()
  const [isHovered, setIsHovered] = useState(false)

  const isSelected = selectedIds.has(media.FileID)
  const video = isVideo(media.MIMEType)

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

  return (
    <motion.div
      className={cn(
        'relative aspect-square overflow-hidden rounded-sm cursor-pointer',
        'select-none group',
        isSelected && 'ring-2 ring-[--color-accent] ring-inset',
      )}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      whileTap={{ scale: 0.97 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.12 }}
    >
      {/* Thumbnail */}
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

      {/* Overlay on hover */}
      <div
        className={cn(
          'absolute inset-0 bg-black/30 photo-overlay',
          isHovered || isSelected ? 'opacity-100' : 'opacity-0',
        )}
      />

      {/* Video indicator */}
      {video && (
        <div className="absolute bottom-1.5 right-1.5 bg-black/60 rounded-full p-1">
          <Play size={10} className="text-white fill-white" />
        </div>
      )}

      {/* Favorite badge */}
      {media.Favorite && !isHovered && !isSelectMode && (
        <div className="absolute top-1.5 right-1.5">
          <Heart size={12} className="text-white fill-white drop-shadow" />
        </div>
      )}

      {/* Hover controls */}
      {(isHovered || isSelectMode) && !isSelected && (
        <motion.button
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.1 }}
          onClick={handleFavorite}
          className={cn(
            'absolute top-1.5 right-1.5 p-1 rounded-full',
            'bg-black/40 hover:bg-black/60 transition-colors',
            'text-white',
          )}
          aria-label={media.Favorite ? 'Remove from favorites' : 'Add to favorites'}
        >
          <Heart
            size={12}
            className={cn(media.Favorite ? 'fill-white' : 'fill-transparent')}
          />
        </motion.button>
      )}

      {/* Selection checkbox */}
      {(isSelectMode || isHovered) && (
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ type: 'spring', bounce: 0, duration: 0.15 }}
          className="absolute top-1.5 left-1.5"
        >
          {isSelected ? (
            <CheckCircle2 size={18} className="text-white fill-[--color-accent] drop-shadow" />
          ) : (
            <div className="w-4.5 h-4.5 rounded-full border-2 border-white/70 bg-black/20" />
          )}
        </motion.div>
      )}
    </motion.div>
  )
}


import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { Trash2, RotateCcw, AlertTriangle, Play } from 'lucide-react'
import { useMediaSearch, useRestoreMedia, usePermanentDeleteMedia } from '@/hooks/useMedia'
import { AuthImage } from '@/components/shared/AuthImage'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'
import { formatBytes, isVideo } from '@/lib/utils'
import type { Media } from '@/types'

function TrashItemCard({
  media,
  onRestore,
  onPermanentDelete,
}: {
  media: Media
  onRestore: (media: Media) => void
  onPermanentDelete: (media: Media) => void
}) {
  const video = isVideo(media.MIMEType)

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.9 }}
      transition={{ duration: 0.18 }}
      className="group relative flex flex-col bg-[--color-surface-overlay] border border-[--color-border-subtle] rounded-[--radius-lg] overflow-hidden hover:border-[--color-border-default] transition-colors"
    >
      <div className="relative aspect-square w-full bg-[--color-surface-base] overflow-hidden">
        <AuthImage
          mediaId={media.FileID}
          type={media.ThumbnailAvailable ? 'thumbnail' : media.PreviewAvailable ? 'preview' : 'original'}
          alt={media.Filename}
          className="absolute inset-0 w-full h-full object-cover"
          placeholder={<div className="absolute inset-0 skeleton" />}
        />
        {video && (
          <div className="absolute bottom-2 left-2 flex items-center gap-1 bg-black/60 backdrop-blur-xs px-1.5 py-0.5 rounded text-[11px] text-white">
            <Play size={10} className="fill-white" />
            <span>Video</span>
          </div>
        )}
      </div>

      <div className="p-3 flex flex-col flex-1 justify-between gap-2">
        <div>
          <p className="text-xs font-medium text-[--color-text-primary] truncate" title={media.Filename}>
            {media.Filename}
          </p>
          <p className="text-[11px] text-[--color-text-muted] mt-0.5">
            {formatBytes(media.SizeBytes)}
          </p>
        </div>

        <div className="flex items-center gap-1.5 pt-1 border-t border-[--color-border-subtle]">
          <Button
            size="sm"
            variant="ghost"
            className="flex-1 text-xs h-7 gap-1 text-[--color-text-secondary] hover:text-[--color-text-primary]"
            onClick={() => onRestore(media)}
          >
            <RotateCcw size={12} />
            Restore
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="text-xs h-7 px-2 text-[--color-danger] hover:bg-red-950/30"
            title="Delete permanently"
            onClick={() => onPermanentDelete(media)}
          >
            <Trash2 size={12} />
          </Button>
        </div>
      </div>
    </motion.div>
  )
}

export function TrashPage() {
  const { addToast } = useUIStore()
  const { data, isLoading } = useMediaSearch({ deleted: true })
  const restoreMutation = useRestoreMedia()
  const permanentDeleteMutation = usePermanentDeleteMedia()

  const [confirmDeleteMedia, setConfirmDeleteMedia] = useState<Media | null>(null)

  const mediaItems = data?.media ?? []

  const handleRestore = async (media: Media) => {
    try {
      await restoreMutation.mutateAsync(media.FileID)
      addToast({ type: 'success', message: `Restored ${media.Filename}` })
    } catch {
      addToast({ type: 'error', message: 'Failed to restore item' })
    }
  }

  const handleConfirmPermanentDelete = async () => {
    if (!confirmDeleteMedia) return
    const target = confirmDeleteMedia
    setConfirmDeleteMedia(null)
    try {
      await permanentDeleteMutation.mutateAsync(target.FileID)
      addToast({ type: 'success', message: `Permanently deleted ${target.Filename}` })
    } catch {
      addToast({ type: 'error', message: 'Failed to permanently delete item' })
    }
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <Trash2 size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Trash</h1>
          {mediaItems.length > 0 && (
            <span className="text-xs text-[--color-text-muted] bg-[--color-surface-subtle] px-2 py-0.5 rounded-full font-mono">
              {mediaItems.length}
            </span>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="aspect-square bg-[--color-surface-overlay] rounded-[--radius-lg] animate-pulse" />
            ))}
          </div>
        ) : mediaItems.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full py-24 gap-4 text-center">
            <div className="w-14 h-14 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
              <Trash2 size={22} className="text-[--color-text-disabled]" />
            </div>
            <div className="space-y-1 max-w-xs">
              <h3 className="text-sm font-medium text-[--color-text-primary]">Trash is empty</h3>
              <p className="text-xs text-[--color-text-muted]">
                Items you delete will be moved here and can be restored or permanently removed at any time.
              </p>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            <AnimatePresence>
              {mediaItems.map((media) => (
                <TrashItemCard
                  key={media.FileID}
                  media={media}
                  onRestore={handleRestore}
                  onPermanentDelete={(m) => setConfirmDeleteMedia(m)}
                />
              ))}
            </AnimatePresence>
          </div>
        )}
      </div>

      {/* Permanent Delete Confirmation Dialog */}
      <AnimatePresence>
        {confirmDeleteMedia && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-sm bg-[--color-surface-overlay] border border-[--color-border-default] rounded-[--radius-xl] p-5 space-y-4 shadow-xl"
            >
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-full bg-red-950/50 flex items-center justify-center text-[--color-danger]">
                  <AlertTriangle size={18} />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-[--color-text-primary]">Delete Permanently?</h3>
                  <p className="text-xs text-[--color-text-muted] truncate max-w-[220px]">
                    {confirmDeleteMedia.Filename}
                  </p>
                </div>
              </div>

              <p className="text-xs text-[--color-text-secondary] leading-relaxed">
                This action cannot be undone. The media file and its derivatives will be permanently removed from storage.
              </p>

              <div className="flex items-center justify-end gap-2 pt-1">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setConfirmDeleteMedia(null)}
                >
                  Cancel
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={handleConfirmPermanentDelete}
                >
                  Delete forever
                </Button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}

import { useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { X, CheckCircle2, AlertCircle, RefreshCw, Upload } from 'lucide-react'
import { useUploadStore } from '@/stores/upload'
import { checkFileExists, uploadFile } from '@/api/client'
import { hashFile, formatBytes } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { UploadItem } from '@/types'

const MAX_CONCURRENT = 3

// ─── Upload engine (processes queue) ─────────────────────────────────────────

export function useUploadEngine() {
  const { queue, updateItem } = useUploadStore()
  const processingRef = useRef<Set<string>>(new Set())

  useEffect(() => {
    const pending = queue.filter(
      (i) => i.status === 'hashing' && !processingRef.current.has(i.id),
    )
    const slots = MAX_CONCURRENT - processingRef.current.size

    pending.slice(0, slots).forEach((item) => {
      processingRef.current.add(item.id)
      processItem(item, updateItem).finally(() => processingRef.current.delete(item.id))
    })
  })
}

async function processItem(
  item: UploadItem,
  updateItem: (id: string, patch: Partial<UploadItem>) => void,
) {
  try {
    // 1. Hash
    updateItem(item.id, { status: 'hashing' })
    const hash = await hashFile(item.file)
    updateItem(item.id, { hash })

    // 2. Check existence (dedup)
    updateItem(item.id, { status: 'checking' })
    const existence = await checkFileExists(hash)
    if (existence.exists) {
      updateItem(item.id, { status: 'duplicate', fileId: existence.file_id, progress: 100 })
      return
    }

    // 3. Upload
    updateItem(item.id, { status: 'uploading', progress: 0 })
    const result = await uploadFile(item.file, (progress) => {
      updateItem(item.id, { progress })
    })
    updateItem(item.id, { status: 'complete', fileId: result.file_id, progress: 100 })
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Upload failed'
    updateItem(item.id, { status: 'error', error: message })
  }
}

// ─── Single queue item UI ─────────────────────────────────────────────────────

function QueueItem({ item }: { item: UploadItem }) {
  const { removeItem, updateItem } = useUploadStore()

  const retry = () => updateItem(item.id, { status: 'hashing', progress: 0, error: undefined })

  const statusIcon = () => {
    switch (item.status) {
      case 'complete': return <CheckCircle2 size={14} className="text-[--color-success]" />
      case 'duplicate': return <CheckCircle2 size={14} className="text-[--color-text-muted]" />
      case 'error': return <AlertCircle size={14} className="text-[--color-danger]" />
      default: return null
    }
  }

  const statusLabel = () => {
    switch (item.status) {
      case 'hashing': return 'Hashing…'
      case 'checking': return 'Checking…'
      case 'uploading': return `${item.progress}%`
      case 'complete': return 'Done'
      case 'duplicate': return 'Already exists'
      case 'error': return item.error ?? 'Error'
      case 'cancelled': return 'Cancelled'
    }
  }

  const isDone = item.status === 'complete' || item.status === 'duplicate'

  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.2 }}
      className="overflow-hidden"
    >
      <div className="px-4 py-2.5 flex items-start gap-3">
        {/* File icon */}
        <div className={cn(
          'w-7 h-7 rounded-[--radius-sm] flex-shrink-0 flex items-center justify-center text-[10px] font-bold',
          'bg-[--color-surface-subtle] text-[--color-text-muted]',
        )}>
          {item.file.name.split('.').pop()?.toUpperCase().slice(0, 3) ?? 'FILE'}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <p className="text-xs text-[--color-text-primary] truncate">{item.file.name}</p>
          <div className="flex items-center gap-1.5 mt-0.5">
            {statusIcon()}
            <span className={cn(
              'text-xs',
              item.status === 'error' ? 'text-[--color-danger]' : 'text-[--color-text-muted]',
            )}>
              {statusLabel()}
            </span>
            <span className="text-[10px] text-[--color-text-disabled]">
              · {formatBytes(item.file.size)}
            </span>
          </div>

          {/* Progress bar */}
          {item.status === 'uploading' && (
            <div className="mt-1.5 h-0.5 bg-[--color-surface-subtle] rounded-full overflow-hidden">
              <motion.div
                className="h-full bg-[--color-accent] rounded-full"
                style={{ width: `${item.progress}%` }}
                transition={{ duration: 0.15 }}
              />
            </div>
          )}

          {/* Indeterminate bar for hashing/checking */}
          {(item.status === 'hashing' || item.status === 'checking') && (
            <div className="mt-1.5 h-0.5 bg-[--color-surface-subtle] rounded-full overflow-hidden">
              <motion.div
                className="h-full w-1/3 bg-[--color-text-disabled] rounded-full"
                animate={{ x: ['0%', '200%'] }}
                transition={{ repeat: Infinity, duration: 1, ease: 'linear' }}
              />
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1 flex-shrink-0">
          {item.status === 'error' && (
            <button
              onClick={retry}
              className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
              aria-label="Retry"
            >
              <RefreshCw size={12} />
            </button>
          )}
          {(isDone || item.status === 'error') && (
            <button
              onClick={() => removeItem(item.id)}
              className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
              aria-label="Remove"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>
    </motion.div>
  )
}

// ─── Drop zone overlay ────────────────────────────────────────────────────────

function DropZone() {
  const { addFiles } = useUploadStore()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFiles = (files: File[]) => {
    const media = files.filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
    if (media.length > 0) addFiles(media)
  }

  return (
    <div
      className={[
        'mx-4 border-2 border-dashed border-[--color-border-default] rounded-[--radius-lg]',
        'p-6 flex flex-col items-center gap-2 cursor-pointer',
        'hover:border-[--color-accent] hover:bg-[--color-surface-overlay] transition-colors',
        'active:scale-[0.98] active:transition-transform active:duration-[80ms]',
      ].join(' ')}
      onClick={() => fileInputRef.current?.click()}
    >
      <Upload size={20} className="text-[--color-text-muted]" />
      <p className="text-xs text-[--color-text-secondary] text-center">
        Click to select files<br />
        <span className="text-[--color-text-muted]">or drag & drop anywhere</span>
      </p>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,video/*"
        className="sr-only"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          handleFiles(files)
          e.target.value = ''
        }}
      />
    </div>
  )
}

// ─── Upload queue panel ───────────────────────────────────────────────────────

interface UploadQueueProps {
  onClose: () => void
}

export function UploadQueue({ onClose }: UploadQueueProps) {
  const { queue, clearCompleted } = useUploadStore()
  const completedCount = queue.filter(
    (i) => i.status === 'complete' || i.status === 'duplicate',
  ).length

  // Start the engine
  useUploadEngine()

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 h-11 border-b border-[--color-border-subtle] flex-shrink-0">
        <span className="text-sm font-medium text-[--color-text-primary]">Uploads</span>
        <div className="flex items-center gap-1">
          {completedCount > 0 && (
            <Button size="sm" variant="ghost" onClick={clearCompleted}>
              Clear
            </Button>
          )}
          <Button size="icon" variant="ghost" onClick={onClose}>
            <X size={14} />
          </Button>
        </div>
      </div>

      {/* Drop zone */}
      <div className="py-3">
        <DropZone />
      </div>

      {/* Queue */}
      {queue.length > 0 && (
        <>
          <div className="px-4 py-2">
            <p className="text-[10px] text-[--color-text-disabled] uppercase tracking-widest font-semibold">
              Queue — {queue.length} item{queue.length > 1 ? 's' : ''}
            </p>
          </div>
          <div className="flex-1 overflow-y-auto">
            <AnimatePresence mode="popLayout">
              {queue.map((item) => (
                <QueueItem key={item.id} item={item} />
              ))}
            </AnimatePresence>
          </div>
        </>
      )}
    </div>
  )
}


import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { X, CheckCircle2, AlertCircle, RefreshCw, Upload, FolderOpen } from 'lucide-react'
import { useUploadStore } from '@/stores/upload'
import { useStorageStore } from '@/stores/storage'
import { checkFileExists, uploadFile } from '@/api/client'
import { hashFileInWorker } from '@/lib/hashWorker'
import { formatBytes, cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { LibrarySelector } from '@/features/storage/LibrarySelector'
import type { UploadItem } from '@/types'

const MAX_CONCURRENT = 3

// ─── Upload engine ────────────────────────────────────────────────────────────
// Runs as a side-effect in UploadQueue. Picks pending items and processes them
// up to MAX_CONCURRENT at a time. Hashing runs in a Web Worker (non-blocking).

export function useUploadEngine() {
  const { queue, updateItem } = useUploadStore()
  const { selectedLibraryId } = useStorageStore()
  const processingRef = useRef<Set<string>>(new Set())

  useEffect(() => {
    const pending = queue.filter(
      (i) => i.status === 'hashing' && !processingRef.current.has(i.id),
    )
    const available = MAX_CONCURRENT - processingRef.current.size
    if (available <= 0 || pending.length === 0) return

    pending.slice(0, available).forEach((item) => {
      processingRef.current.add(item.id)
      processItem(item, updateItem, selectedLibraryId ?? undefined).finally(() => {
        processingRef.current.delete(item.id)
      })
    })
  })
}

async function processItem(
  item: UploadItem,
  updateItem: (id: string, patch: Partial<UploadItem>) => void,
  storageId: string | undefined,
) {
  try {
    // 1. Hash in worker — non-blocking
    updateItem(item.id, { status: 'hashing' })
    const hash = await hashFileInWorker(item.file)
    updateItem(item.id, { hash })

    // 2. Deduplication check
    updateItem(item.id, { status: 'checking' })
    const existence = await checkFileExists(hash)
    if (existence.exists) {
      updateItem(item.id, { status: 'duplicate', fileId: existence.file_id, progress: 100 })
      return
    }

    // 3. Upload with storage destination
    updateItem(item.id, { status: 'uploading', progress: 0 })
    const result = await uploadFile(item.file, storageId, (progress) => {
      updateItem(item.id, { progress })
    })
    updateItem(item.id, { status: 'complete', fileId: result.file_id, progress: 100 })
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Upload failed'
    updateItem(item.id, { status: 'error', error: message })
  }
}

// ─── Queue item ───────────────────────────────────────────────────────────────

function QueueItem({ item }: { item: UploadItem }) {
  const { removeItem, updateItem } = useUploadStore()
  const retry = () => updateItem(item.id, { status: 'hashing', progress: 0, error: undefined })

  const isDone = item.status === 'complete' || item.status === 'duplicate'

  const statusIcon = () => {
    switch (item.status) {
      case 'complete': return <CheckCircle2 size={13} className="text-[--color-success]" />
      case 'duplicate': return <CheckCircle2 size={13} className="text-[--color-text-muted]" />
      case 'error': return <AlertCircle size={13} className="text-[--color-danger]" />
      default: return null
    }
  }

  const statusLabel = () => {
    switch (item.status) {
      case 'hashing':   return 'Hashing…'
      case 'checking':  return 'Checking…'
      case 'uploading': return `${item.progress}%`
      case 'complete':  return 'Done'
      case 'duplicate': return 'Already exists'
      case 'error':     return item.error ?? 'Error'
      case 'cancelled': return 'Cancelled'
    }
  }

  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.18 }}
      className="overflow-hidden"
    >
      <div className="px-4 py-2.5 flex items-start gap-3">
        <div className={cn(
          'w-7 h-7 rounded-[--radius-sm] flex-shrink-0 flex items-center justify-center',
          'text-[10px] font-bold bg-[--color-surface-subtle] text-[--color-text-muted]',
        )}>
          {item.file.name.split('.').pop()?.toUpperCase().slice(0, 4) ?? 'FILE'}
        </div>

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

          {/* Determinate progress */}
          {item.status === 'uploading' && (
            <div className="mt-1.5 h-0.5 bg-[--color-surface-subtle] rounded-full overflow-hidden">
              <motion.div
                className="h-full bg-[--color-accent] rounded-full"
                style={{ width: `${item.progress}%` }}
                transition={{ duration: 0.12 }}
              />
            </div>
          )}

          {/* Indeterminate bar */}
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

        <div className="flex items-center gap-1 flex-shrink-0 pt-0.5">
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

// ─── Drop zone ────────────────────────────────────────────────────────────────

function DropZone() {
  const { addFiles } = useUploadStore()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const folderInputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  const handleFiles = (files: File[]) => {
    const media = files.filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
    if (media.length > 0) addFiles(media)
  }

  return (
    <div className="mx-4 space-y-2">
      <div
        className={cn(
          'border-2 border-dashed rounded-[--radius-lg] p-4',
          'flex flex-col items-center gap-1.5',
          'transition-colors duration-[120ms] cursor-default',
          dragging
            ? 'border-[--color-accent] bg-[--color-surface-subtle]'
            : 'border-[--color-border-default] hover:border-[--color-border-bright]',
        )}
        onDragEnter={(e) => { e.preventDefault(); setDragging(true) }}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={(e) => { e.preventDefault(); setDragging(false) }}
        onDrop={(e) => {
          e.preventDefault()
          e.stopPropagation()
          setDragging(false)
          handleFiles(Array.from(e.dataTransfer.files))
        }}
      >
        <Upload size={16} className={dragging ? 'text-[--color-accent]' : 'text-[--color-text-muted]'} />
        <p className="text-xs text-[--color-text-muted] text-center">
          Drop photos & videos here
        </p>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <button
          onClick={() => fileInputRef.current?.click()}
          className={cn(
            'flex items-center justify-center gap-1.5 h-8 rounded-[--radius-md] text-xs',
            'bg-[--color-surface-overlay] border border-[--color-border-default]',
            'text-[--color-text-secondary] hover:text-[--color-text-primary]',
            'hover:bg-[--color-surface-subtle] transition-colors',
            'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
          )}
        >
          <Upload size={12} />
          Files
        </button>
        <button
          onClick={() => folderInputRef.current?.click()}
          className={cn(
            'flex items-center justify-center gap-1.5 h-8 rounded-[--radius-md] text-xs',
            'bg-[--color-surface-overlay] border border-[--color-border-default]',
            'text-[--color-text-secondary] hover:text-[--color-text-primary]',
            'hover:bg-[--color-surface-subtle] transition-colors',
            'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
          )}
        >
          <FolderOpen size={12} />
          Folder
        </button>
      </div>

      <input ref={fileInputRef} type="file" multiple accept="image/*,video/*" className="sr-only"
        onChange={(e) => { handleFiles(Array.from(e.target.files ?? [])); e.target.value = '' }} />
      <input ref={folderInputRef} type="file"
        // @ts-expect-error — webkitdirectory is non-standard but universally supported
        webkitdirectory="" multiple className="sr-only"
        onChange={(e) => { handleFiles(Array.from(e.target.files ?? [])); e.target.value = '' }} />
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

  useUploadEngine()

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* Header */}
      <div className="flex items-center justify-between px-4 h-11 border-b border-[--color-border-subtle] flex-shrink-0">
        <span className="text-sm font-medium text-[--color-text-primary]">Uploads</span>
        <div className="flex items-center gap-1">
          {completedCount > 0 && (
            <Button size="sm" variant="ghost" onClick={clearCompleted}>Clear</Button>
          )}
          <Button size="icon" variant="ghost" onClick={onClose}><X size={14} /></Button>
        </div>
      </div>

      {/* Library selector */}
      <div className="pt-3 pb-1">
        <LibrarySelector />
      </div>

      {/* Drop zone */}
      <div className="pt-2 pb-3">
        <DropZone />
      </div>

      {/* Queue */}
      {queue.length > 0 && (
        <>
          <div className="px-4 py-1.5 border-t border-[--color-border-subtle]">
            <p className="text-[10px] text-[--color-text-disabled] uppercase tracking-widest font-semibold">
              Queue — {queue.length} item{queue.length !== 1 ? 's' : ''}
            </p>
          </div>
          <div className="flex-1 overflow-y-auto scrollbar-none">
            <AnimatePresence mode="popLayout">
              {queue.map((item) => <QueueItem key={item.id} item={item} />)}
            </AnimatePresence>
          </div>
        </>
      )}
    </div>
  )
}

import { useState } from 'react'
import { motion } from 'motion/react'
import {
  HardDrive, CheckCircle2, AlertTriangle, ChevronDown,
  Pencil, Star, ShieldCheck, RefreshCw, Trash2, Check, X,
} from 'lucide-react'
import {
  useRenameStorage,
  useSetDefaultStorage,
  useVerifyStorage,
  useRescanStorage,
  useDeleteStorage,
} from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { formatBytes, cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { StorageLibrary } from '@/types/storage'

interface LibraryCardProps {
  library: StorageLibrary
}

// ─── Inline rename input ──────────────────────────────────────────────────────

function RenameInput({
  initial,
  onSave,
  onCancel,
}: {
  initial: string
  onSave: (name: string) => void
  onCancel: () => void
}) {
  const [value, setValue] = useState(initial)
  return (
    <form
      className="flex items-center gap-1.5"
      onSubmit={(e) => { e.preventDefault(); if (value.trim()) onSave(value.trim()) }}
    >
      <input
        autoFocus
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className={cn(
          'flex-1 px-2 h-7 text-sm bg-[--color-surface-subtle]',
          'text-[--color-text-primary] border border-[--color-border-bright]',
          'rounded-[--radius-md] focus:outline-none focus:border-[--color-accent]',
        )}
      />
      <button type="submit" className="p-1 text-[--color-success]" aria-label="Save">
        <Check size={13} />
      </button>
      <button type="button" onClick={onCancel} className="p-1 text-[--color-text-muted]" aria-label="Cancel">
        <X size={13} />
      </button>
    </form>
  )
}

// ─── Library card ─────────────────────────────────────────────────────────────

export function LibraryCard({ library }: LibraryCardProps) {
  const { addToast } = useUIStore()
  const [expanded, setExpanded] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const rename = useRenameStorage()
  const setDefault = useSetDefaultStorage()
  const verify = useVerifyStorage()
  const rescan = useRescanStorage()
  const deleteLib = useDeleteStorage()

  const handleRename = async (name: string) => {
    await rename.mutateAsync({ id: library.id, payload: { name } })
    setRenaming(false)
    addToast({ type: 'success', message: `Renamed to "${name}"` })
  }

  const handleSetDefault = async () => {
    await setDefault.mutateAsync(library.id)
    addToast({ type: 'success', message: `"${library.name}" set as default` })
  }

  const handleVerify = async () => {
    const result = await verify.mutateAsync(library.id)
    addToast({
      type: result.healthy ? 'success' : 'error',
      message: result.healthy ? `"${library.name}" is healthy` : `"${library.name}" has issues`,
    })
  }

  const handleRescan = async () => {
    await rescan.mutateAsync(library.id)
    addToast({ type: 'info', message: `Rescanning "${library.name}"…` })
  }

  const handleDelete = async () => {
    if (!confirmDelete) { setConfirmDelete(true); return }
    await deleteLib.mutateAsync(library.id)
    addToast({ type: 'success', message: `"${library.name}" removed` })
  }

  return (
    <motion.div
      layout
      className="bg-[--color-surface-overlay] rounded-[--radius-xl] border border-[--color-border-default] overflow-hidden"
    >
      {/* Main row */}
      <div className="flex items-center gap-4 px-5 py-4">
        {/* Icon */}
        <div className={cn(
          'w-9 h-9 rounded-[--radius-lg] flex-shrink-0 flex items-center justify-center',
          library.healthy ? 'bg-[--color-surface-subtle]' : 'bg-[--color-danger-surface]',
        )}>
          <HardDrive
            size={16}
            className={library.healthy ? 'text-[--color-text-secondary]' : 'text-[--color-danger]'}
          />
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          {renaming ? (
            <RenameInput
              initial={library.name}
              onSave={handleRename}
              onCancel={() => setRenaming(false)}
            />
          ) : (
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-[--color-text-primary] truncate">
                {library.name}
              </p>
              {library.default && (
                <span className="text-[10px] font-semibold text-[--color-text-muted] bg-[--color-surface-subtle] px-1.5 py-0.5 rounded-full uppercase tracking-wide">
                  Default
                </span>
              )}
            </div>
          )}
          <p className="text-xs text-[--color-text-muted] font-mono mt-0.5 truncate">
            {library.path}
          </p>
        </div>

        {/* Status + expand */}
        <div className="flex items-center gap-2 flex-shrink-0">
          {library.healthy ? (
            <div className="flex items-center gap-1.5 text-[--color-success]">
              <CheckCircle2 size={13} />
              <span className="text-xs hidden sm:block">Healthy</span>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 text-[--color-danger]">
              <AlertTriangle size={13} />
              <span className="text-xs hidden sm:block">Unavailable</span>
            </div>
          )}
          <button
            onClick={() => setExpanded((s) => !s)}
            className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
            aria-label={expanded ? 'Collapse' : 'Expand'}
          >
            <ChevronDown
              size={14}
              className={cn('transition-transform duration-[150ms]', expanded && 'rotate-180')}
            />
          </button>
        </div>
      </div>

      {/* Expanded stats + actions */}
      <motion.div
        initial={false}
        animate={{ height: expanded ? 'auto' : 0, opacity: expanded ? 1 : 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
        className="overflow-hidden"
      >
        <div className="px-5 pb-4 pt-1 border-t border-[--color-border-subtle] space-y-4">
          {/* Stats */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-[--color-surface-subtle] rounded-[--radius-md] px-3 py-2">
              <p className="text-[10px] text-[--color-text-muted] uppercase tracking-wide mb-0.5">Available</p>
              <p className="text-sm font-semibold text-[--color-text-primary]">
                {library.free_space > 0 ? formatBytes(library.free_space) : '—'}
              </p>
            </div>
            <div className="bg-[--color-surface-subtle] rounded-[--radius-md] px-3 py-2">
              <p className="text-[10px] text-[--color-text-muted] uppercase tracking-wide mb-0.5">Status</p>
              <p className={cn(
                'text-sm font-semibold',
                library.healthy ? 'text-[--color-success]' : 'text-[--color-danger]',
              )}>
                {library.healthy ? '✓ Healthy' : '⚠ Unavailable'}
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="ghost" onClick={() => setRenaming(true)} disabled={renaming}>
              <Pencil size={12} />
              Rename
            </Button>
            {!library.default && (
              <Button size="sm" variant="ghost" onClick={handleSetDefault} loading={setDefault.isPending}>
                <Star size={12} />
                Set default
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={handleVerify} loading={verify.isPending}>
              <ShieldCheck size={12} />
              Verify
            </Button>
            <Button size="sm" variant="ghost" onClick={handleRescan} loading={rescan.isPending}>
              <RefreshCw size={12} />
              Rescan
            </Button>
            <Button
              size="sm"
              variant={confirmDelete ? 'destructive' : 'ghost'}
              onClick={handleDelete}
              loading={deleteLib.isPending}
              onBlur={() => setConfirmDelete(false)}
              className="ml-auto"
            >
              <Trash2 size={12} />
              {confirmDelete ? 'Confirm remove' : 'Remove'}
            </Button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}

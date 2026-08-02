import { useRef, useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { ChevronDown, CheckCircle2, AlertTriangle, HardDrive } from 'lucide-react'
import { useStorages } from '@/hooks/useStorage'
import { useStorageStore } from '@/stores/storage'
import { formatBytes, cn } from '@/lib/utils'
import type { StorageLibrary } from '@/types/storage'

// ─── Storage status icon ─────────────────────────────────────────────────────

function StatusDot({ healthy }: { healthy: boolean }) {
  return (
    <span
      className={cn(
        'w-1.5 h-1.5 rounded-full flex-shrink-0',
        healthy ? 'bg-[--color-success]' : 'bg-[--color-danger]',
      )}
    />
  )
}

// ─── Dropdown option ─────────────────────────────────────────────────────────

function LibraryOption({
  library,
  selected,
  onSelect,
}: {
  library: StorageLibrary
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      onClick={onSelect}
      className={cn(
        'w-full flex items-center gap-2.5 px-3 py-2 text-left',
        'transition-colors duration-[100ms]',
        selected
          ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
          : 'text-[--color-text-secondary] hover:bg-[--color-surface-overlay] hover:text-[--color-text-primary]',
      )}
    >
      <StatusDot healthy={library.healthy} />
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium truncate">{library.name}</p>
        {library.free_space > 0 && (
          <p className="text-[10px] text-[--color-text-disabled]">
            {formatBytes(library.free_space)} free
          </p>
        )}
      </div>
      {selected && <CheckCircle2 size={13} className="text-[--color-accent] flex-shrink-0" />}
    </button>
  )
}

// ─── Main selector ────────────────────────────────────────────────────────────

export function LibrarySelector() {
  const { data: libraries = [], isLoading } = useStorages()
  const { selectedLibraryId, setSelectedLibraryId } = useStorageStore()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // Resolve the active library — fall back to default, then first
  const activeLibrary: StorageLibrary | null =
    libraries.find((l) => l.id === selectedLibraryId) ??
    libraries.find((l) => l.default) ??
    libraries[0] ??
    null

  // Auto-set selected to default on first load
  useEffect(() => {
    if (!selectedLibraryId && activeLibrary) {
      setSelectedLibraryId(activeLibrary.id)
    }
  }, [activeLibrary, selectedLibraryId, setSelectedLibraryId])

  // Close on outside click
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    if (open) document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  if (isLoading || libraries.length === 0) return null

  const unavailable = activeLibrary && !activeLibrary.healthy

  return (
    <div className="mx-4">
      <p className="text-[10px] text-[--color-text-disabled] uppercase tracking-widest font-semibold mb-1.5">
        Destination
      </p>

      {/* Unavailable banner */}
      {unavailable && (
        <div className="flex items-center gap-2 px-3 py-2 mb-2 rounded-[--radius-md] bg-[--color-danger-surface] border border-red-900">
          <AlertTriangle size={12} className="text-[--color-danger] flex-shrink-0" />
          <p className="text-[11px] text-[--color-danger] leading-tight">
            Storage unavailable — reconnect the drive or choose another library.
          </p>
        </div>
      )}

      {/* Selector button */}
      <div ref={ref} className="relative">
        <button
          onClick={() => libraries.length > 1 && setOpen((s) => !s)}
          className={cn(
            'w-full flex items-center gap-2 px-3 h-8 rounded-[--radius-md] text-xs',
            'bg-[--color-surface-overlay] border border-[--color-border-default]',
            'transition-colors duration-[100ms]',
            libraries.length > 1
              ? 'hover:bg-[--color-surface-subtle] cursor-pointer active:scale-[0.97] active:transition-transform active:duration-[80ms]'
              : 'cursor-default',
          )}
          aria-haspopup={libraries.length > 1 ? 'listbox' : undefined}
          aria-expanded={open}
        >
          <HardDrive size={11} className="text-[--color-text-muted] flex-shrink-0" />
          {activeLibrary && <StatusDot healthy={activeLibrary.healthy} />}
          <span className="flex-1 text-left text-[--color-text-primary] truncate font-medium">
            {activeLibrary?.name ?? 'Select library'}
          </span>
          {libraries.length > 1 && (
            <ChevronDown
              size={12}
              className={cn(
                'text-[--color-text-muted] transition-transform duration-[150ms]',
                open && 'rotate-180',
              )}
            />
          )}
        </button>

        {/* Dropdown */}
        <AnimatePresence>
          {open && (
            <motion.div
              key="library-dropdown"
              initial={{ opacity: 0, y: -4, scaleY: 0.95 }}
              animate={{ opacity: 1, y: 0, scaleY: 1 }}
              exit={{ opacity: 0, y: -4, scaleY: 0.95 }}
              transition={{ type: 'spring', bounce: 0, duration: 0.18 }}
              style={{ transformOrigin: 'top' }}
              className={cn(
                'absolute top-full mt-1 left-0 right-0 z-20',
                'bg-[--color-surface-overlay] border border-[--color-border-default]',
                'rounded-[--radius-lg] shadow-[--shadow-3] overflow-hidden',
              )}
              role="listbox"
            >
              {libraries.map((lib) => (
                <LibraryOption
                  key={lib.id}
                  library={lib}
                  selected={lib.id === activeLibrary?.id}
                  onSelect={() => {
                    setSelectedLibraryId(lib.id)
                    setOpen(false)
                  }}
                />
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

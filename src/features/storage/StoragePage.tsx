import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { HardDrive, Plus, ArrowRight, FolderOpen, X } from 'lucide-react'
import { useStorages, useCreateStorage, useSelectFolder } from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { LibraryCard } from './LibraryCard'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

// ─── Add library dialog ───────────────────────────────────────────────────────

interface AddLibraryDialogProps {
  onClose: () => void
}

function AddLibraryDialog({ onClose }: AddLibraryDialogProps) {
  const { addToast } = useUIStore()
  const [name, setName] = useState('')
  const [path, setPath] = useState('')

  const selectFolder = useSelectFolder()
  const createStorage = useCreateStorage()

  const handleSelectFolder = async () => {
    try {
      const result = await selectFolder.mutateAsync()
      setPath(result.path)
    } catch {
      addToast({ type: 'error', message: 'Could not open folder picker' })
    }
  }

  const handleCreate = async () => {
    if (!name.trim() || !path) return
    try {
      await createStorage.mutateAsync({ name: name.trim(), path })
      addToast({ type: 'success', message: `"${name.trim()}" library added` })
      onClose()
    } catch {
      addToast({ type: 'error', message: 'Failed to create library' })
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 8 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
        className="w-full max-w-md bg-[--color-surface-overlay] border border-[--color-border-default] rounded-[--radius-2xl] shadow-[--shadow-4] p-6 space-y-5"
      >
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-[--color-text-primary]">Add library</h2>
          <button
            onClick={onClose}
            className="p-1 text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors"
          >
            <X size={15} />
          </button>
        </div>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Name</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Archive"
              autoFocus
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Folder</label>
            {path ? (
              <div className="flex items-center gap-2 px-3 py-2 bg-[--color-surface-subtle] rounded-[--radius-md] border border-[--color-border-default]">
                <FolderOpen size={13} className="text-[--color-text-muted] flex-shrink-0" />
                <span className="text-xs text-[--color-text-secondary] font-mono truncate flex-1">{path}</span>
                <button
                  onClick={handleSelectFolder}
                  className="text-xs text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors flex-shrink-0"
                >
                  Change
                </button>
              </div>
            ) : (
              <Button
                variant="outline"
                size="md"
                className="w-full"
                onClick={handleSelectFolder}
                loading={selectFolder.isPending}
              >
                <FolderOpen size={13} />
                Choose folder on server
              </Button>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 pt-1">
          <Button variant="ghost" size="md" onClick={onClose} className="flex-1">Cancel</Button>
          <Button
            variant="accent"
            size="md"
            className="flex-1"
            onClick={handleCreate}
            loading={createStorage.isPending}
            disabled={!name.trim() || !path}
          >
            Create library
          </Button>
        </div>
      </motion.div>
    </motion.div>
  )
}

// ─── Storage page ─────────────────────────────────────────────────────────────

export function StoragePage() {
  const { data: libraries = [], isLoading } = useStorages()
  const [showAdd, setShowAdd] = useState(false)

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <HardDrive size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Storage</h1>
          {libraries.length > 0 && (
            <span className="text-xs text-[--color-text-muted]">
              · {libraries.length} {libraries.length === 1 ? 'library' : 'libraries'}
            </span>
          )}
        </div>
        <Button size="sm" variant="ghost" onClick={() => setShowAdd(true)}>
          <Plus size={12} />
          Add library
        </Button>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-6">
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <div key={i} className="h-20 skeleton rounded-[--radius-xl]" />
            ))}
          </div>
        ) : libraries.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-4 text-center">
            <div className="w-14 h-14 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
              <HardDrive size={22} className="text-[--color-text-disabled]" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-medium text-[--color-text-primary]">No libraries configured</p>
              <p className="text-xs text-[--color-text-muted] max-w-48">
                Add a storage library to start uploading photos.
              </p>
            </div>
            <Button variant="accent" size="md" onClick={() => setShowAdd(true)}>
              <Plus size={13} />
              Add library
              <ArrowRight size={13} />
            </Button>
          </div>
        ) : (
          <div className="space-y-3 max-w-2xl">
            <AnimatePresence mode="popLayout">
              {libraries.map((lib) => (
                <LibraryCard key={lib.id} library={lib} />
              ))}
            </AnimatePresence>

            <p className="text-xs text-[--color-text-disabled] pt-2">
              Removing a library does not delete files from disk.
            </p>
          </div>
        )}
      </div>

      {/* Add dialog */}
      <AnimatePresence>
        {showAdd && <AddLibraryDialog onClose={() => setShowAdd(false)} />}
      </AnimatePresence>
    </div>
  )
}

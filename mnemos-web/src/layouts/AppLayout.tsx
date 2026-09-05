import { useState, useRef, useEffect, useCallback } from 'react'
import { Outlet, Link, useRouter } from '@tanstack/react-router'
import { motion, AnimatePresence } from 'motion/react'
import {
  Images,
  Clock,
  Heart,
  Trash2,
  Upload,
  FolderUp,
  Monitor,
  Settings,
  HardDrive,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Toasts } from '@/components/ui/Toasts'
import { useUploadStore } from '@/stores/upload'
import { useAuthStore } from '@/stores/auth'
import { UploadQueue, useUploadEngine } from '@/features/upload/UploadQueue'

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
}

function NavLink({ to, label, icon }: NavItem) {
  const router = useRouter()
  const isActive =
    router.state.location.pathname === to ||
    (to !== '/' && router.state.location.pathname.startsWith(to))

  return (
    <Link
      to={to}
      className={cn(
        'flex items-center gap-3 px-3.5 h-9 rounded-[--radius-md] text-xs font-medium',
        'transition-all duration-150 ease-out select-none',
        isActive
          ? 'bg-[--color-surface-subtle] text-[--color-text-primary] shadow-xs font-semibold'
          : 'text-[--color-text-secondary] hover:bg-[--color-surface-overlay] hover:text-[--color-text-primary]',
      )}
    >
      <span className={cn('w-4 h-4 flex-shrink-0 transition-opacity', isActive ? 'opacity-100 text-[--color-accent]' : 'opacity-70')}>
        {icon}
      </span>
      <span className="flex-1">{label}</span>
    </Link>
  )
}

const LIBRARY_NAV: NavItem[] = [
  { to: '/gallery', label: 'Photos & Media', icon: <Images size={15} /> },
  { to: '/timeline', label: 'Timeline', icon: <Clock size={15} /> },
  { to: '/favorites', label: 'Favorites', icon: <Heart size={15} /> },
]

const SYSTEM_NAV: NavItem[] = [
  { to: '/devices', label: 'Clients & Devices', icon: <Monitor size={15} /> },
  { to: '/trash', label: 'Trash', icon: <Trash2 size={15} /> },
  { to: '/settings', label: 'Server & Settings', icon: <Settings size={15} /> },
]

export function AppLayout() {
  useUploadEngine()
  const { session } = useAuthStore()
  const { queue, isOpen, setOpen, addFiles } = useUploadStore()
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const folderInputRef = useRef<HTMLInputElement>(null)

  // Collapsible sidebar — persisted across reloads
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem('photovault_sidebar_open')
      return saved === null ? true : saved === 'true'
    } catch {
      return true
    }
  })

  const toggleSidebar = useCallback(() => {
    setSidebarOpen((prev) => {
      const next = !prev
      try { localStorage.setItem('photovault_sidebar_open', String(next)) } catch {}
      return next
    })
  }, [])

  // Cmd+B / Ctrl+B keyboard shortcut
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
        e.preventDefault()
        toggleSidebar()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [toggleSidebar])

  const activeUploads = queue.filter(
    (i) => i.status === 'uploading' || i.status === 'hashing' || i.status === 'checking',
  ).length

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const files = Array.from(e.dataTransfer.files)
    if (files.length > 0) {
      addFiles(files)
      setOpen(true)
    }
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    if (e.currentTarget.contains(e.relatedTarget as Node)) return
    setIsDragging(false)
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      addFiles(Array.from(e.target.files))
      setOpen(true)
      e.target.value = ''
    }
  }

  const handleFolderInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const files = Array.from(e.target.files).filter((file) => {
        const type = file.type.toLowerCase()
        const name = file.name.toLowerCase()
        return (
          type.startsWith('image/') ||
          type.startsWith('video/') ||
          /\.(jpe?g|png|gif|webp|heic|heif|mp4|mov|m4v|avi|mkv)$/i.test(name)
        )
      })
      if (files.length > 0) {
        addFiles(files)
        setOpen(true)
      }
      e.target.value = ''
    }
  }

  return (
    <div
      className="flex h-screen bg-[--color-surface-base] text-[--color-text-primary] overflow-hidden select-none"
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
    >
      {/* ── Sidebar ── */}
      <aside
        className="flex-shrink-0 flex flex-col border-r border-[--color-border-subtle] bg-[--color-surface-base] overflow-hidden"
        style={{
          width: sidebarOpen ? '15rem' : '0',
          minWidth: sidebarOpen ? '15rem' : '0',
          transition: 'width 200ms cubic-bezier(0.23, 1, 0.32, 1), min-width 200ms cubic-bezier(0.23, 1, 0.32, 1)',
        }}
      >
        <div
          style={{
            width: '15rem',
            opacity: sidebarOpen ? 1 : 0,
            transition: 'opacity 150ms cubic-bezier(0.23, 1, 0.32, 1)',
            display: 'flex',
            flexDirection: 'column',
            flex: 1,
            overflow: 'hidden',
          }}
        >
          {/* Brand & Server Header */}
          <div className="p-4 border-b border-[--color-border-subtle]">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-[--radius-md] bg-white flex items-center justify-center shadow-sm flex-shrink-0">
                <Images size={17} className="text-black" />
              </div>
              <div className="min-w-0 flex-1">
                <span className="text-sm font-bold tracking-tight text-[--color-text-primary] block leading-none truncate">
                  PhotoVault
                </span>
                <div className="flex items-center gap-1.5 mt-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse flex-shrink-0" />
                  <span className="text-[11px] text-[--color-text-muted] font-medium">Server Online</span>
                </div>
              </div>
            </div>
          </div>

          {/* Upload & Import Buttons */}
          <div className="px-3 pt-4 pb-2 space-y-2">
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/*,video/*"
              className="hidden"
              onChange={handleFileInput}
            />
            <input
              ref={folderInputRef}
              type="file"
              // @ts-expect-error webkitdirectory is non-standard but widely supported in all major browsers
              webkitdirectory=""
              directory=""
              multiple
              className="hidden"
              onChange={handleFolderInput}
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              className={cn(
                'w-full flex items-center justify-center gap-2 px-4 h-9 rounded-[--radius-md]',
                'bg-white text-black font-semibold text-xs',
                'hover:bg-white/90 active:scale-[0.97] transition-all shadow-xs cursor-pointer',
              )}
            >
              <Upload size={14} />
              <span>Upload Photos</span>
              {activeUploads > 0 && (
                <span className="ml-1 bg-black/20 text-black px-1.5 py-0.2 rounded-full text-[10px] font-mono">
                  {activeUploads}
                </span>
              )}
            </button>
            <button
              onClick={() => folderInputRef.current?.click()}
              className={cn(
                'w-full flex items-center justify-center gap-2 px-4 h-8 rounded-[--radius-md]',
                'bg-[--color-surface-subtle] border border-[--color-border-subtle] text-[--color-text-primary] font-medium text-xs',
                'hover:bg-[--color-surface-overlay] active:scale-[0.97] transition-all shadow-xs cursor-pointer',
              )}
            >
              <FolderUp size={14} className="text-[--color-accent]" />
              <span>Import Folder</span>
            </button>
          </div>

          {/* Navigation Items */}
          <nav className="flex-1 px-3 py-2 space-y-4 overflow-y-auto scrollbar-none">
            <div className="space-y-0.5">
              <span className="px-3 text-[10px] uppercase font-bold tracking-wider text-[--color-text-muted] block mb-1">
                Library
              </span>
              {LIBRARY_NAV.map((item) => (
                <NavLink key={item.to} {...item} />
              ))}
            </div>

            <div className="space-y-0.5">
              <span className="px-3 text-[10px] uppercase font-bold tracking-wider text-[--color-text-muted] block mb-1">
                System
              </span>
              {SYSTEM_NAV.map((item) => (
                <NavLink key={item.to} {...item} />
              ))}
            </div>
          </nav>

          {/* Active Uploads Drawer Toggle (if items in queue) */}
          {queue.length > 0 && (
            <div className="px-3 py-2 border-t border-[--color-border-subtle]">
              <button
                onClick={() => setOpen(!isOpen)}
                className="w-full flex items-center justify-between px-3 h-8 rounded-[--radius-md] text-xs bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary] cursor-pointer active:scale-[0.97] transition-all"
              >
                <span>Upload Queue</span>
                <span className="font-mono text-[11px] font-semibold text-[--color-accent]">
                  {activeUploads > 0 ? `${activeUploads} uploading` : 'Complete'}
                </span>
              </button>
            </div>
          )}

          {/* Current Device Session Footer */}
          {session && (
            <div className="p-3 border-t border-[--color-border-subtle] bg-[--color-surface-overlay]/50">
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-[--radius-sm] bg-[--color-surface-subtle] flex items-center justify-center text-[--color-text-secondary] flex-shrink-0">
                  <HardDrive size={13} />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-semibold text-[--color-text-primary] truncate">
                    {session.deviceName}
                  </p>
                  <p className="text-[10px] text-[--color-text-muted] truncate">
                    Client ID: {session.deviceId.slice(0, 8)}…
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>
      </aside>

      {/* ── Main content view ── */}
      <main className="flex-1 overflow-hidden relative flex flex-col">
        {/* Top bar with sidebar toggle */}
        <div className="flex items-center gap-2 px-4 py-2 border-b border-[--color-border-subtle] bg-[--color-surface-base]/80 backdrop-blur-sm">
          <button
            onClick={toggleSidebar}
            title={sidebarOpen ? 'Collapse sidebar (⌘B)' : 'Expand sidebar (⌘B)'}
            className="p-1.5 rounded-[--radius-md] text-[--color-text-muted] hover:text-[--color-text-primary] hover:bg-[--color-surface-subtle] active:scale-[0.97] transition-all cursor-pointer"
          >
            {sidebarOpen ? <PanelLeftClose size={16} /> : <PanelLeftOpen size={16} />}
          </button>
        </div>
        <div className="flex-1 overflow-hidden relative">
        <Outlet />

        {/* Drag & drop overlay */}
        <AnimatePresence>
          {isDragging && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 z-50 flex flex-col items-center justify-center bg-black/75 backdrop-blur-sm pointer-events-none"
            >
              <div className="w-16 h-16 rounded-2xl bg-[--color-accent]/20 border border-[--color-accent]/40 flex items-center justify-center mb-3">
                <Upload size={32} className="text-[--color-accent] animate-bounce" />
              </div>
              <h3 className="text-base font-semibold text-white">Drop photos or videos to upload</h3>
              <p className="text-xs text-white/60 mt-1">Files are automatically deduplicated and indexed</p>
            </motion.div>
          )}
        </AnimatePresence>
        </div>
      </main>

      {/* ── Upload Queue Drawer ── */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="upload-queue"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.2 }}
            className="w-80 flex-shrink-0 border-l border-[--color-border-subtle] bg-[--color-surface-overlay] overflow-hidden shadow-xl"
          >
            <UploadQueue onClose={() => setOpen(false)} />
          </motion.div>
        )}
      </AnimatePresence>

      <Toasts />
    </div>
  )
}

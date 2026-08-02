import { Outlet, Link, useRouter } from '@tanstack/react-router'
import { motion, AnimatePresence } from 'motion/react'
import {
  Images,
  Clock,
  Heart,
  Search,
  Trash2,
  Upload,
  Monitor,
  Lock,
  Settings,
  RefreshCw,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Toasts } from '@/components/ui/Toasts'
import { useUploadStore } from '@/stores/upload'
import { UploadQueue } from '@/features/upload/UploadQueue'

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
  badge?: number
}

function NavLink({ to, label, icon, badge }: NavItem) {
  const router = useRouter()
  const isActive = router.state.location.pathname === to ||
    (to !== '/' && router.state.location.pathname.startsWith(to))

  return (
    <Link
      to={to}
      className={cn(
        'flex items-center gap-2.5 px-3 h-8 rounded-[--radius-md] text-sm',
        'transition-colors duration-[120ms] ease-out select-none',
        isActive
          ? 'bg-[--color-surface-subtle] text-[--color-text-primary]'
          : 'text-[--color-text-secondary] hover:bg-[--color-surface-overlay] hover:text-[--color-text-primary]',
      )}
    >
      <span className="w-4 h-4 flex-shrink-0 opacity-70">{icon}</span>
      <span className="flex-1">{label}</span>
      {badge !== undefined && badge > 0 && (
        <span className="text-xs text-[--color-text-muted] tabular-nums">{badge}</span>
      )}
    </Link>
  )
}

const NAV_SECTIONS = [
  {
    items: [
      { to: '/gallery', label: 'Library', icon: <Images size={15} /> },
      { to: '/timeline', label: 'Timeline', icon: <Clock size={15} /> },
      { to: '/search', label: 'Search', icon: <Search size={15} /> },
    ],
  },
  {
    label: 'Collections',
    items: [
      { to: '/favorites', label: 'Favorites', icon: <Heart size={15} /> },
      { to: '/trash', label: 'Trash', icon: <Trash2 size={15} /> },
    ],
  },
  {
    label: 'System',
    items: [
      { to: '/sync', label: 'Sync', icon: <RefreshCw size={15} /> },
      { to: '/devices', label: 'Devices', icon: <Monitor size={15} /> },
      { to: '/vaults', label: 'Vaults', icon: <Lock size={15} /> },
      { to: '/settings', label: 'Settings', icon: <Settings size={15} /> },
    ],
  },
]

export function AppLayout() {
  const { queue, isOpen, setOpen } = useUploadStore()
  const activeUploads = queue.filter(
    (i) => i.status === 'uploading' || i.status === 'hashing' || i.status === 'checking',
  ).length

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const files = Array.from(e.dataTransfer.files)
    if (files.length > 0) {
      useUploadStore.getState().addFiles(files)
    }
  }

  const handleDragOver = (e: React.DragEvent) => e.preventDefault()

  return (
    <div
      className="flex h-screen bg-[--color-surface-base] overflow-hidden"
      onDrop={handleDrop}
      onDragOver={handleDragOver}
    >
      {/* ── Sidebar ── */}
      <aside className="w-56 flex-shrink-0 flex flex-col border-r border-[--color-border-subtle] py-4">
        {/* Logo */}
        <div className="px-4 mb-6">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-[--radius-sm] bg-[--color-accent] flex items-center justify-center">
              <Images size={13} className="text-[--color-surface-base]" />
            </div>
            <span className="text-sm font-semibold tracking-tight text-[--color-text-primary]">
              Mnemos
            </span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-2 space-y-5 overflow-y-auto scrollbar-none">
          {NAV_SECTIONS.map((section, i) => (
            <div key={i}>
              {section.label && (
                <p className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-[--color-text-disabled]">
                  {section.label}
                </p>
              )}
              <div className="space-y-0.5">
                {section.items.map((item) => (
                  <NavLink key={item.to} {...item} />
                ))}
              </div>
            </div>
          ))}
        </nav>

        {/* Upload trigger */}
        <div className="px-2 mt-4">
          <button
            onClick={() => {
              setOpen(true)
            }}
            className={cn(
              'w-full flex items-center gap-2.5 px-3 h-8 rounded-[--radius-md] text-sm',
              'text-[--color-text-secondary] hover:bg-[--color-surface-overlay]',
              'hover:text-[--color-text-primary] transition-colors duration-[120ms]',
              'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
            )}
          >
            <Upload size={15} className="opacity-70" />
            <span className="flex-1 text-left">Upload</span>
            {activeUploads > 0 && (
              <span className="text-xs text-[--color-warning] tabular-nums">{activeUploads}</span>
            )}
          </button>
        </div>
      </aside>

      {/* ── Main content ── */}
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>

      {/* ── Upload queue panel ── */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="upload-queue"
            initial={{ opacity: 0, x: 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 16 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="w-80 flex-shrink-0 border-l border-[--color-border-subtle] overflow-hidden"
          >
            <UploadQueue onClose={() => setOpen(false)} />
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Toasts ── */}
      <Toasts />
    </div>
  )
}


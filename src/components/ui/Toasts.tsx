import { AnimatePresence, motion } from 'motion/react'
import { CheckCircle, XCircle, Info, X } from 'lucide-react'
import { useUIStore } from '@/stores/ui'

const icons = {
  success: CheckCircle,
  error: XCircle,
  info: Info,
}

const colors = {
  success: 'text-[--color-success]',
  error: 'text-[--color-danger]',
  info: 'text-[--color-text-secondary]',
}

export function Toasts() {
  const { toasts, removeToast } = useUIStore()

  return (
    <div
      className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 pointer-events-none"
      aria-live="polite"
    >
      <AnimatePresence mode="popLayout">
        {toasts.map((toast) => {
          const Icon = icons[toast.type]
          return (
            <motion.div
              key={toast.id}
              layout
              initial={{ opacity: 0, y: 12, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 4, scale: 0.98 }}
              transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
              className={[
                'pointer-events-auto flex items-center gap-3',
                'bg-[--color-surface-overlay] border border-[--color-border-default]',
                'rounded-[--radius-lg] shadow-[--shadow-3] px-4 py-3',
                'min-w-64 max-w-sm',
              ].join(' ')}
            >
              <Icon size={16} className={colors[toast.type]} />
              <p className="text-sm text-[--color-text-primary] flex-1">{toast.message}</p>
              <button
                onClick={() => removeToast(toast.id)}
                className="text-[--color-text-muted] hover:text-[--color-text-primary] transition-colors ml-1"
                aria-label="Dismiss"
              >
                <X size={14} />
              </button>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}


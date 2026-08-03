import { Trash2 } from 'lucide-react'

export function TrashPage() {
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Trash2 size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Trash</h1>
      </div>
      <div className="flex-1 flex flex-col items-center justify-center gap-4 text-center p-12">
        <div className="w-14 h-14 rounded-[--radius-2xl] bg-[--color-surface-overlay] flex items-center justify-center">
          <Trash2 size={22} className="text-[--color-text-disabled]" />
        </div>
        <div className="space-y-1 max-w-xs">
          <h3 className="text-sm font-medium text-[--color-text-primary]">Trash view coming soon</h3>
          <p className="text-xs text-[--color-text-muted]">
            The backend supports soft deletion, but the API does not yet expose a dedicated
            endpoint to list deleted items. Deleted photos are retained on disk and will
            appear here once the backend adds a{' '}
            <code className="text-[--color-text-secondary] text-[11px]">deleted=true</code> filter to{' '}
            <code className="text-[--color-text-secondary] text-[11px]">GET /media</code>.
          </p>
        </div>
      </div>
    </div>
  )
}


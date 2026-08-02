import { useState } from 'react'
import { RefreshCw, CheckCircle2, AlertCircle, Clock } from 'lucide-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { getSyncDiff, ackSync } from '@/api/client'
import { Button } from '@/components/ui/Button'
import { formatRelative, formatBytes } from '@/lib/utils'
import { useUIStore } from '@/stores/ui'
import type { SyncFile } from '@/types'

function SyncFileRow({ file }: { file: SyncFile }) {
  const takenDate = new Date(file.uploaded_at)
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 hover:bg-[--color-surface-overlay] transition-colors rounded-[--radius-md]">
      <div className="w-7 h-7 rounded-[--radius-sm] bg-[--color-surface-subtle] flex items-center justify-center text-[9px] font-bold text-[--color-text-muted] flex-shrink-0">
        {file.filename.split('.').pop()?.toUpperCase().slice(0, 4) ?? 'FILE'}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs text-[--color-text-primary] truncate">{file.filename}</p>
        <p className="text-[11px] text-[--color-text-muted]">
          {formatBytes(file.size_bytes)} · {formatRelative(takenDate)}
          {file.thumbnail_available && ' · thumb'}
        </p>
      </div>
      <div className="flex gap-1 flex-shrink-0">
        {file.thumbnail_available && (
          <span className="text-[10px] bg-[--color-surface-subtle] text-[--color-text-muted] px-1.5 py-0.5 rounded-full">T</span>
        )}
        {file.preview_available && (
          <span className="text-[10px] bg-[--color-surface-subtle] text-[--color-text-muted] px-1.5 py-0.5 rounded-full">P</span>
        )}
      </div>
    </div>
  )
}

export function SyncPage() {
  const { addToast } = useUIStore()
  const [since, setSince] = useState<number | undefined>(undefined)

  const {
    data,
    isLoading,
    isFetching,
    refetch,
    dataUpdatedAt,
  } = useQuery({
    queryKey: ['sync-diff', since],
    queryFn: () => getSyncDiff(since, 100),
    staleTime: 30_000,
  })

  const ackMutation = useMutation({
    mutationFn: (ids: string[]) => ackSync(ids),
    onSuccess: (result) => {
      addToast({ type: 'success', message: `Acknowledged ${result.acknowledged} file${result.acknowledged !== 1 ? 's' : ''}` })
      refetch()
    },
    onError: () => {
      addToast({ type: 'error', message: 'Failed to acknowledge sync' })
    },
  })

  const files = data?.files ?? []
  const hasMore = data?.next_since !== undefined

  const handleAckAll = () => {
    if (files.length === 0) return
    ackMutation.mutate(files.map((f) => f.file_id))
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <RefreshCw size={15} className={`text-[--color-text-muted] ${isFetching ? 'animate-spin' : ''}`} />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Sync</h1>
        </div>
        <div className="flex items-center gap-2">
          {dataUpdatedAt > 0 && (
            <span className="text-xs text-[--color-text-disabled]">
              Updated {formatRelative(new Date(dataUpdatedAt))}
            </span>
          )}
          <Button size="sm" variant="ghost" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw size={12} className={isFetching ? 'animate-spin' : ''} />
            Refresh
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {/* Stats bar */}
        <div className="px-6 py-4 grid grid-cols-3 gap-4 border-b border-[--color-border-subtle]">
          {[
            { label: 'Pending', value: files.length, icon: <Clock size={14} /> },
            { label: 'Status', value: isFetching ? 'Syncing' : 'Idle', icon: <RefreshCw size={14} /> },
            {
              label: 'Has more',
              value: hasMore ? 'Yes' : 'No',
              icon: hasMore ? <AlertCircle size={14} className="text-[--color-warning]" /> : <CheckCircle2 size={14} className="text-[--color-success]" />,
            },
          ].map(({ label, value, icon }) => (
            <div key={label} className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4">
              <div className="flex items-center gap-1.5 text-[--color-text-muted] mb-1">
                {icon}
                <span className="text-xs">{label}</span>
              </div>
              <p className="text-lg font-semibold text-[--color-text-primary] tabular-nums">{value}</p>
            </div>
          ))}
        </div>

        {/* Actions */}
        {files.length > 0 && (
          <div className="px-6 py-3 flex items-center justify-between border-b border-[--color-border-subtle]">
            <p className="text-xs text-[--color-text-muted]">
              {files.length} unsynced file{files.length !== 1 ? 's' : ''}
              {hasMore ? ' (more available)' : ''}
            </p>
            <Button
              size="sm"
              variant="accent"
              onClick={handleAckAll}
              loading={ackMutation.isPending}
            >
              <CheckCircle2 size={12} />
              Acknowledge all
            </Button>
          </div>
        )}

        {/* File list */}
        {isLoading ? (
          <div className="p-6 space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-10 skeleton rounded-[--radius-md]" />
            ))}
          </div>
        ) : files.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 gap-3 text-center">
            <CheckCircle2 size={28} className="text-[--color-success]" />
            <div>
              <p className="text-sm font-medium text-[--color-text-primary]">All caught up</p>
              <p className="text-xs text-[--color-text-muted] mt-1">
                This device is synchronized with the server.
              </p>
            </div>
          </div>
        ) : (
          <div className="p-2">
            {files.map((file) => (
              <SyncFileRow key={file.file_id} file={file} />
            ))}
            {hasMore && data?.next_since && (
              <div className="px-4 py-3 text-center">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setSince(data.next_since)}
                >
                  Load more
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}


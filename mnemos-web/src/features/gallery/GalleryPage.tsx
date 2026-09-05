import { useState, useMemo, useCallback, useEffect, useRef } from 'react'
import { Virtuoso } from 'react-virtuoso'
import {
  Images,
  Image as ImageIcon,
  Film,
  Download,
  Trash2,
  Heart,
  Search,
  X,
  Smartphone,
  Laptop,
  CheckSquare,
  Grid3X3,
  LayoutGrid,
  List,
  ArrowUpDown,
  MapPin,
  Camera,
  Check,
  ChevronDown,
} from 'lucide-react'
import { useMediaInfinite, useFavoriteMedia, useDeleteMedia } from '@/hooks/useMedia'
import { useUIStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { downloadMedia, ackSync, getMediaURL } from '@/api/client'
import { groupByMonth, formatBytes, isVideo } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import { PhotoTile } from './PhotoTile'
import { MediaViewer } from '@/features/viewer/MediaViewer'
import type { Media, MediaGroup } from '@/types'

type FilterTab = 'all' | 'photos' | 'videos' | 'others' | 'self' | 'favorites'

type SortOption = {
  label: string
  sort: 'taken_at' | 'uploaded_at' | 'size_bytes' | 'filename' | 'location'
  order: 'asc' | 'desc'
}

const SORT_OPTIONS: SortOption[] = [
  { label: 'Date Taken (Newest)', sort: 'taken_at', order: 'desc' },
  { label: 'Date Taken (Oldest)', sort: 'taken_at', order: 'asc' },
  { label: 'Date Added (Newest)', sort: 'uploaded_at', order: 'desc' },
  { label: 'Date Added (Oldest)', sort: 'uploaded_at', order: 'asc' },
  { label: 'Location (GPS First)', sort: 'location', order: 'desc' },
  { label: 'File Size (Largest)', sort: 'size_bytes', order: 'desc' },
  { label: 'File Size (Smallest)', sort: 'size_bytes', order: 'asc' },
  { label: 'File Name (A to Z)', sort: 'filename', order: 'asc' },
  { label: 'File Name (Z to A)', sort: 'filename', order: 'desc' },
]

const GAP = 8

function PhotoRow({ items, cols }: { items: Media[]; cols: number }) {
  return (
    <div
      className="grid px-6 pb-2"
      style={{
        gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`,
        gap: `${GAP}px`,
      }}
    >
      {items.map((media) => (
        <PhotoTile key={media.FileID} media={media} />
      ))}
    </div>
  )
}

type VirtRow =
  | { type: 'header'; label: string }
  | { type: 'row'; items: Media[] }

function buildVirtRows(groups: MediaGroup[], cols: number): VirtRow[] {
  const rows: VirtRow[] = []
  for (const group of groups) {
    rows.push({ type: 'header', label: group.label })
    for (let i = 0; i < group.items.length; i += cols) {
      rows.push({ type: 'row', items: group.items.slice(i, i + cols) })
    }
  }
  return rows
}

interface MediaListRowProps {
  media: Media
  isSelected: boolean
  onSelect: () => void
  isSelectMode: boolean
  onOpenViewer: () => void
  onFavorite: () => void
  onDownload: () => void
  onDelete: () => void
}

function MediaListRow({
  media,
  isSelected,
  onSelect,
  isSelectMode,
  onOpenViewer,
  onFavorite,
  onDownload,
  onDelete,
}: MediaListRowProps) {
  const isVid = isVideo(media.MIMEType)
  const dateStr = media.TakenAt || media.UploadedAt
  const formattedDate = dateStr
    ? new Date(dateStr).toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : 'Unknown date'

  return (
    <div
      onClick={isSelectMode ? onSelect : onOpenViewer}
      className={`group flex items-center gap-3 px-6 py-2 border-b border-[--color-border-subtle]/50 hover:bg-[--color-surface-subtle] transition-colors cursor-pointer ${
        isSelected ? 'bg-[--color-accent]/10' : ''
      }`}
    >
      {/* Checkbox in select mode */}
      {isSelectMode && (
        <input
          type="checkbox"
          checked={isSelected}
          onChange={onSelect}
          onClick={(e) => e.stopPropagation()}
          className="w-4 h-4 rounded border-[--color-border-default] text-[--color-accent] focus:ring-0 cursor-pointer shrink-0"
        />
      )}

      {/* Small Thumbnail */}
      <div className="relative w-10 h-10 rounded-[--radius-md] overflow-hidden bg-[--color-surface-base] border border-[--color-border-subtle] shrink-0">
        <img
          src={getMediaURL(media.FileID, media.ThumbnailAvailable ? 'thumbnail' : 'original')}
          alt={media.Filename}
          className="w-full h-full object-cover"
          loading="lazy"
        />
        {isVid && (
          <div className="absolute inset-0 bg-black/40 flex items-center justify-center text-white">
            <Film size={12} />
          </div>
        )}
      </div>

      {/* Filename and Metadata */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-[--color-text-primary] truncate group-hover:text-[--color-accent] transition-colors">
            {media.Filename}
          </span>
          {media.Favorite && (
            <Heart size={11} className="text-rose-500 fill-rose-500 shrink-0" />
          )}
        </div>
        <div className="flex items-center gap-2 text-[11px] text-[--color-text-muted] mt-0.5 truncate">
          <span>{formattedDate}</span>
          <span>•</span>
          <span>{formatBytes(media.SizeBytes)}</span>
          {media.Width && media.Height && (
            <>
              <span>•</span>
              <span>{media.Width}×{media.Height}</span>
            </>
          )}
        </div>
      </div>

      {/* Camera / Place Info */}
      <div className="hidden md:flex items-center gap-3 text-xs text-[--color-text-muted] shrink-0 max-w-xs truncate">
        {media.GPSLat != null && media.GPSLon != null && (
          <span
            className="inline-flex items-center gap-1 text-[11px] font-mono text-[--color-text-secondary]"
            title={`GPS: ${media.GPSLat}, ${media.GPSLon}`}
          >
            <MapPin size={12} className="text-[--color-accent]" />
            {media.GPSLat.toFixed(2)}°, {media.GPSLon.toFixed(2)}°
          </span>
        )}
        {media.CameraModel && (
          <span
            className="inline-flex items-center gap-1 text-[11px] text-[--color-text-secondary] truncate"
            title={media.CameraModel}
          >
            <Camera size={12} />
            {media.CameraModel}
          </span>
        )}
      </div>

      {/* Device Origin Badge */}
      <div className="hidden sm:flex items-center shrink-0">
        <span className="inline-flex items-center gap-1 text-[10px] font-mono px-2 py-0.5 rounded-full bg-[--color-surface-base] border border-[--color-border-subtle] text-[--color-text-secondary]">
          {media.UploadedByDeviceName || 'Server Host'}
        </span>
      </div>

      {/* Row Quick Actions */}
      <div
        className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onFavorite}
          className="p-1.5 text-[--color-text-muted] hover:text-rose-400 rounded-[--radius-sm] hover:bg-[--color-surface-base] transition-colors cursor-pointer"
          title={media.Favorite ? 'Unfavorite' : 'Favorite'}
        >
          <Heart size={13} className={media.Favorite ? 'fill-rose-500 text-rose-500' : ''} />
        </button>
        <button
          onClick={onDownload}
          className="p-1.5 text-[--color-text-muted] hover:text-[--color-text-primary] rounded-[--radius-sm] hover:bg-[--color-surface-base] transition-colors cursor-pointer"
          title="Download original"
        >
          <Download size={13} />
        </button>
        <button
          onClick={onDelete}
          className="p-1.5 text-[--color-text-muted] hover:text-rose-400 rounded-[--radius-sm] hover:bg-[--color-surface-base] transition-colors cursor-pointer"
          title="Move to trash"
        >
          <Trash2 size={13} />
        </button>
      </div>
    </div>
  )
}

export function GalleryPage() {
  const { session } = useAuthStore()
  const {
    isSelectMode,
    toggleSelectMode,
    selectedIds,
    clearSelection,
    selectAll,
    toggleSelect,
    openViewer,
    addToast,
  } = useUIStore()

  const [activeTab, setActiveTab] = useState<FilterTab>('all')
  const [searchQuery, setSearchQuery] = useState('')

  // View mode: 'grid' vs 'list'
  const [viewMode, setViewMode] = useState<'grid' | 'list'>(() => {
    const saved = localStorage.getItem('photovault_gallery_view_mode')
    return saved === 'list' ? 'list' : 'grid'
  })

  const handleSetViewMode = (mode: 'grid' | 'list') => {
    setViewMode(mode)
    localStorage.setItem('photovault_gallery_view_mode', mode)
  }

  // Grid columns (4, 5, 6)
  const [cols, setCols] = useState<number>(() => {
    const saved = localStorage.getItem('photovault_gallery_cols')
    return saved ? Math.min(Math.max(Number(saved), 3), 7) : 5
  })

  useEffect(() => {
    localStorage.setItem('photovault_gallery_cols', String(cols))
  }, [cols])

  // Sorting
  const [selectedSort, setSelectedSort] = useState<SortOption>(SORT_OPTIONS[0])
  const [showSortMenu, setShowSortMenu] = useState(false)
  const sortMenuRef = useRef<HTMLDivElement>(null)

  // Close sort menu on click outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (sortMenuRef.current && !sortMenuRef.current.contains(event.target as Node)) {
        setShowSortMenu(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Downloaded tracking for "From Others"
  const [downloadedSet, setDownloadedSet] = useState<Set<string>>(() => {
    try {
      const saved = localStorage.getItem('photovault_downloaded_ids')
      return saved ? new Set(JSON.parse(saved)) : new Set()
    } catch {
      return new Set()
    }
  })

  const markAsDownloaded = useCallback((fileIds: string[]) => {
    setDownloadedSet((prev) => {
      const next = new Set(prev)
      fileIds.forEach((id) => next.add(id))
      try {
        localStorage.setItem('photovault_downloaded_ids', JSON.stringify(Array.from(next)))
      } catch {}
      return next
    })
    ackSync(fileIds).catch(() => {})
  }, [])

  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()

  // Build query params based on active tab, search, and sorting
  const queryParams = useMemo(() => {
    const params: {
      sort: 'taken_at' | 'uploaded_at' | 'filename' | 'size_bytes' | 'location'
      order: 'desc' | 'asc'
      query?: string
      favorite?: boolean
      mime_type?: string
      device_id?: string
      exclude_device_id?: string
    } = {
      sort: selectedSort.sort,
      order: selectedSort.order,
    }

    if (searchQuery.trim()) {
      params.query = searchQuery.trim()
    }

    if (activeTab === 'photos') {
      params.mime_type = 'image/'
    } else if (activeTab === 'videos') {
      params.mime_type = 'video/'
    } else if (activeTab === 'others' && session?.deviceId) {
      params.exclude_device_id = session.deviceId
    } else if (activeTab === 'self' && session?.deviceId) {
      params.device_id = session.deviceId
    } else if (activeTab === 'favorites') {
      params.favorite = true
    }

    return params
  }, [activeTab, searchQuery, session?.deviceId, selectedSort])

  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
  } = useMediaInfinite(queryParams)

  const allMedia = useMemo(
    () => data?.pages.flatMap((p) => p.media) ?? [],
    [data],
  )

  const allIds = useMemo(() => allMedia.map((m) => m.FileID), [allMedia])
  const groups = useMemo(() => groupByMonth(allMedia), [allMedia])
  const virtRows = useMemo(() => buildVirtRows(groups, cols), [groups, cols])

  // Bulk Actions
  const handleBulkDownload = useCallback(() => {
    const selected = allMedia.filter((m) => selectedIds.has(m.FileID))
    if (selected.length === 0) return
    selected.forEach((m) => downloadMedia(m.FileID, m.Filename))
    markAsDownloaded(selected.map((m) => m.FileID))
    addToast({
      type: 'info',
      message: `Downloading ${selected.length} item${selected.length > 1 ? 's' : ''}`,
    })
    clearSelection()
  }, [allMedia, selectedIds, markAsDownloaded, addToast, clearSelection])

  const handleBulkFavorite = useCallback(async () => {
    const ids = Array.from(selectedIds)
    await Promise.allSettled(ids.map((id) => favoriteMedia.mutateAsync({ id, favorite: true })))
    addToast({ type: 'success', message: `Favorited ${ids.length} item${ids.length > 1 ? 's' : ''}` })
    clearSelection()
  }, [selectedIds, favoriteMedia, addToast, clearSelection])

  const handleBulkDelete = useCallback(async () => {
    const ids = Array.from(selectedIds)
    await Promise.allSettled(ids.map((id) => deleteMedia.mutateAsync(id)))
    addToast({ type: 'success', message: `Moved ${ids.length} item${ids.length > 1 ? 's' : ''} to trash` })
    clearSelection()
  }, [selectedIds, deleteMedia, addToast, clearSelection])

  // Un-downloaded items from other devices
  const unDownloadedItems = useMemo(
    () => allMedia.filter((m) => !downloadedSet.has(m.FileID)),
    [allMedia, downloadedSet],
  )

  // Download all files from others
  const handleDownloadAllFromOthers = useCallback(() => {
    if (allMedia.length === 0) return
    allMedia.forEach((m) => downloadMedia(m.FileID, m.Filename))
    markAsDownloaded(allMedia.map((m) => m.FileID))
    addToast({
      type: 'info',
      message: `Downloading all ${allMedia.length} file${allMedia.length > 1 ? 's' : ''} from others`,
    })
  }, [allMedia, markAsDownloaded, addToast])

  // Download only new (un-downloaded) files from others
  const handleDownloadNewFromOthers = useCallback(() => {
    if (unDownloadedItems.length === 0) return
    unDownloadedItems.forEach((m) => downloadMedia(m.FileID, m.Filename))
    markAsDownloaded(unDownloadedItems.map((m) => m.FileID))
    addToast({
      type: 'info',
      message: `Downloading ${unDownloadedItems.length} new file${unDownloadedItems.length > 1 ? 's' : ''} to this device`,
    })
  }, [unDownloadedItems, markAsDownloaded, addToast])

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* ── Top Header & Filter Controls ── */}
      <div className="flex flex-col border-b border-[--color-border-subtle] bg-[--color-surface-base]/85 backdrop-blur-md sticky top-0 z-20 px-6 py-3.5 gap-3 shadow-xs">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2.5">
            <h1 className="text-base font-bold text-[--color-text-primary] tracking-tight">Photos &amp; Library</h1>
            <span className="text-xs text-[--color-text-muted] font-mono bg-[--color-surface-subtle] px-2 py-0.5 rounded-full border border-[--color-border-subtle]">
              {allMedia.length.toLocaleString()}
            </span>
          </div>

          {/* Search Input */}
          <div className="relative flex-1 max-w-xs">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[--color-text-muted]" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search library..."
              className="w-full bg-[--color-surface-subtle] text-xs text-[--color-text-primary] pl-8 pr-7 py-1.5 rounded-[--radius-md] border border-[--color-border-subtle] focus:outline-none focus:border-[--color-accent] transition-colors"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-[--color-text-muted] hover:text-[--color-text-primary] cursor-pointer"
              >
                <X size={12} />
              </button>
            )}
          </div>

          {/* Selection, Sorting, View Mode Actions */}
          <div className="flex items-center gap-2">
            {/* Sort Dropdown */}
            <div className="relative" ref={sortMenuRef}>
              <button
                onClick={() => setShowSortMenu((s) => !s)}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[--radius-md] border border-[--color-border-subtle] bg-[--color-surface-subtle] text-xs text-[--color-text-secondary] hover:text-[--color-text-primary] cursor-pointer transition-colors"
                title="Sort media"
              >
                <ArrowUpDown size={13} />
                <span className="hidden sm:inline">{selectedSort.label.split(' ')[0]}</span>
                <ChevronDown size={11} className="text-[--color-text-muted]" />
              </button>

              {showSortMenu && (
                <div
                  className="absolute right-0 top-full mt-1.5 w-56 py-1 rounded-[--radius-lg] z-50 overflow-hidden"
                  style={{
                    background: '#18181b',
                    border: '1px solid rgba(63,63,70,0.8)',
                    boxShadow: '0 20px 60px rgba(0,0,0,0.7), 0 4px 12px rgba(0,0,0,0.5)',
                    transformOrigin: 'top right',
                    animation: 'sortMenuIn 150ms cubic-bezier(0.23, 1, 0.32, 1) both',
                  }}
                >
                  <style>{`
                    @keyframes sortMenuIn {
                      from { opacity: 0; transform: scale(0.96) translateY(-4px); }
                      to   { opacity: 1; transform: scale(1) translateY(0); }
                    }
                  `}</style>
                  <div className="px-3 py-1.5 text-[10px] uppercase font-bold tracking-wider text-zinc-500 border-b border-zinc-700/60">
                    Sort Media By
                  </div>
                  {SORT_OPTIONS.map((opt) => {
                    const isSelected = opt.label === selectedSort.label
                    return (
                      <button
                        key={opt.label}
                        onClick={() => {
                          setSelectedSort(opt)
                          setShowSortMenu(false)
                        }}
                        className={`w-full flex items-center justify-between px-3 py-2 text-xs text-left cursor-pointer transition-colors active:scale-[0.98] ${
                          isSelected
                            ? 'bg-white/10 text-white font-semibold'
                            : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'
                        }`}
                      >
                        <span>{opt.label}</span>
                        {isSelected && <Check size={13} className="text-white shrink-0" />}
                      </button>
                    )
                  })}
                </div>
              )}

            </div>

            {/* View Mode & Density Switcher */}
            <div className="flex items-center gap-0.5 bg-[--color-surface-subtle] p-0.5 rounded-[--radius-md] border border-[--color-border-subtle]">
              <button
                onClick={() => handleSetViewMode('grid')}
                className={`p-1.5 rounded cursor-pointer transition-colors ${
                  viewMode === 'grid'
                    ? 'bg-[--color-surface-overlay] text-[--color-text-primary] shadow-xs'
                    : 'text-[--color-text-muted] hover:text-[--color-text-primary]'
                }`}
                title="Grid View"
              >
                <LayoutGrid size={13} />
              </button>
              <button
                onClick={() => handleSetViewMode('list')}
                className={`p-1.5 rounded cursor-pointer transition-colors ${
                  viewMode === 'list'
                    ? 'bg-[--color-surface-overlay] text-[--color-text-primary] shadow-xs'
                    : 'text-[--color-text-muted] hover:text-[--color-text-primary]'
                }`}
                title="List View"
              >
                <List size={13} />
              </button>

              {/* Grid Density only in Grid mode */}
              {viewMode === 'grid' && (
                <div className="hidden sm:flex items-center gap-0.5 pl-1 border-l border-[--color-border-subtle]">
                  <button
                    onClick={() => setCols(4)}
                    className={`p-1 rounded cursor-pointer transition-colors ${cols === 4 ? 'bg-[--color-surface-overlay] text-[--color-text-primary] shadow-xs' : 'text-[--color-text-muted] hover:text-[--color-text-primary]'}`}
                    title="Comfortable (4 columns)"
                  >
                    <span className="text-[10px] font-mono font-bold px-0.5">4</span>
                  </button>
                  <button
                    onClick={() => setCols(5)}
                    className={`p-1 rounded cursor-pointer transition-colors ${cols === 5 ? 'bg-[--color-surface-overlay] text-[--color-text-primary] shadow-xs' : 'text-[--color-text-muted] hover:text-[--color-text-primary]'}`}
                    title="Standard (5 columns)"
                  >
                    <Grid3X3 size={13} />
                  </button>
                  <button
                    onClick={() => setCols(6)}
                    className={`p-1 rounded cursor-pointer transition-colors ${cols === 6 ? 'bg-[--color-surface-overlay] text-[--color-text-primary] shadow-xs' : 'text-[--color-text-muted] hover:text-[--color-text-primary]'}`}
                    title="Compact (6 columns)"
                  >
                    <span className="text-[10px] font-mono font-bold px-0.5">6</span>
                  </button>
                </div>
              )}
            </div>

            {isSelectMode ? (
              <div className="flex items-center gap-2">
                <span className="text-xs text-[--color-text-secondary] font-medium">
                  {selectedIds.size} selected
                </span>
                <Button size="sm" variant="ghost" onClick={() => selectAll(allIds)}>
                  Select all
                </Button>
                {selectedIds.size > 0 && (
                  <>
                    <Button size="sm" variant="default" onClick={handleBulkDownload}>
                      <Download size={13} />
                      Download
                    </Button>
                    <Button size="sm" variant="ghost" onClick={handleBulkFavorite} title="Favorite selected">
                      <Heart size={13} />
                    </Button>
                    <Button size="sm" variant="destructive" onClick={handleBulkDelete} title="Move selected to trash">
                      <Trash2 size={13} />
                    </Button>
                  </>
                )}
                <Button size="sm" variant="ghost" onClick={clearSelection}>
                  <X size={13} />
                  Done
                </Button>
              </div>
            ) : (
              <Button size="sm" variant="ghost" onClick={toggleSelectMode}>
                <CheckSquare size={14} />
                Select
              </Button>
            )}
          </div>
        </div>

        {/* ── Category & Owner Filter Tabs ── */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5 scrollbar-none">
            <button
              onClick={() => setActiveTab('all')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'all'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <Images size={12} />
              <span>All Media</span>
            </button>

            <button
              onClick={() => setActiveTab('photos')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'photos'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <ImageIcon size={12} />
              <span>Photos Only</span>
            </button>

            <button
              onClick={() => setActiveTab('videos')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'videos'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <Film size={12} />
              <span>Videos</span>
            </button>

            <button
              onClick={() => setActiveTab('favorites')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'favorites'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <Heart size={12} className={activeTab === 'favorites' ? 'fill-black' : ''} />
              <span>Favorites</span>
            </button>

            <button
              onClick={() => setActiveTab('others')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'others'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <Smartphone size={12} />
              <span>From Others</span>
            </button>

            <button
              onClick={() => setActiveTab('self')}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium cursor-pointer transition-all ${
                activeTab === 'self'
                  ? 'bg-white text-black shadow-xs font-semibold'
                  : 'bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
              }`}
            >
              <Laptop size={12} />
              <span>From This Device</span>
            </button>
          </div>

          {/* Selective Download Controls for "From Others" */}
          {activeTab === 'others' && allMedia.length > 0 && (
            <div className="flex items-center gap-1.5">
              {unDownloadedItems.length > 0 ? (
                <Button
                  size="sm"
                  variant="accent"
                  className="text-xs h-7 gap-1.5 font-semibold shadow-xs"
                  onClick={handleDownloadNewFromOthers}
                  title="Download files that are not on this device yet"
                >
                  <Download size={12} />
                  <span>Download New ({unDownloadedItems.length})</span>
                </Button>
              ) : (
                <span className="flex items-center gap-1 text-[11px] font-medium text-emerald-400 bg-emerald-950/40 border border-emerald-900/50 px-2.5 py-1 rounded-full">
                  <Check size={11} />
                  <span>All Downloaded</span>
                </span>
              )}

              <Button
                size="sm"
                variant="ghost"
                className="text-xs h-7 gap-1.5 text-[--color-text-secondary] hover:text-[--color-text-primary]"
                onClick={handleDownloadAllFromOthers}
                title="Download all files again"
              >
                <Download size={12} />
                <span>Download All ({allMedia.length})</span>
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* ── Media Stream: Grid or List ── */}
      <div className="flex-1 overflow-hidden">
        {isLoading ? (
          viewMode === 'grid' ? (
            <div
              className="grid gap-2 p-6"
              style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}
            >
              {Array.from({ length: 20 }).map((_, i) => (
                <div key={i} className="aspect-square bg-[--color-surface-subtle] rounded-[--radius-md] animate-pulse" />
              ))}
            </div>
          ) : (
            <div className="p-6 space-y-2">
              {Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="h-12 bg-[--color-surface-subtle] rounded-[--radius-md] animate-pulse" />
              ))}
            </div>
          )
        ) : allMedia.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-center p-12">
            <div className="w-14 h-14 rounded-2xl bg-[--color-surface-overlay] flex items-center justify-center border border-[--color-border-subtle]">
              {activeTab === 'videos' ? (
                <Film size={24} className="text-[--color-text-disabled]" />
              ) : (
                <Images size={24} className="text-[--color-text-disabled]" />
              )}
            </div>
            <div className="space-y-1 max-w-xs">
              <h3 className="text-sm font-semibold text-[--color-text-primary]">
                {activeTab === 'photos'
                  ? 'No photos found'
                  : activeTab === 'videos'
                  ? 'No videos found'
                  : activeTab === 'others'
                  ? 'No media from other clients'
                  : activeTab === 'self'
                  ? 'No media uploaded from this device'
                  : activeTab === 'favorites'
                  ? 'No favorites yet'
                  : 'No media found'}
              </h3>
              <p className="text-xs text-[--color-text-muted]">
                {activeTab === 'others'
                  ? 'Photos and videos uploaded by other devices on your personal network will appear here.'
                  : 'Drag & drop photos or videos anywhere to upload them directly to your PhotoVault.'}
              </p>
            </div>
          </div>
        ) : viewMode === 'grid' ? (
          <Virtuoso
            data={virtRows}
            endReached={() => {
              if (hasNextPage && !isFetchingNextPage) fetchNextPage()
            }}
            overscan={400}
            itemContent={(_, row) => {
              if (row.type === 'header') {
                return (
                  <div className="px-6 pt-6 pb-2">
                    <h2 className="text-xs font-semibold uppercase tracking-wider text-[--color-text-muted]">
                      {row.label}
                    </h2>
                  </div>
                )
              }
              return <PhotoRow items={row.items} cols={cols} />
            }}
            components={{
              Footer: () =>
                isFetchingNextPage ? (
                  <div className="flex justify-center py-6">
                    <span className="animate-spin h-5 w-5 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
                  </div>
                ) : null,
            }}
          />
        ) : (
          /* ── List View Mode ── */
          <Virtuoso
            data={allMedia}
            endReached={() => {
              if (hasNextPage && !isFetchingNextPage) fetchNextPage()
            }}
            overscan={200}
            itemContent={(_, media) => (
              <MediaListRow
                key={media.FileID}
                media={media}
                isSelected={selectedIds.has(media.FileID)}
                onSelect={() => toggleSelect(media.FileID)}
                isSelectMode={isSelectMode}
                onOpenViewer={() => openViewer(media.FileID)}
                onFavorite={() => favoriteMedia.mutate({ id: media.FileID, favorite: !media.Favorite })}
                onDownload={() => {
                  downloadMedia(media.FileID, media.Filename)
                  markAsDownloaded([media.FileID])
                  addToast({ type: 'info', message: `Downloading ${media.Filename}` })
                }}
                onDelete={() => {
                  deleteMedia.mutate(media.FileID)
                  addToast({ type: 'success', message: 'Moved to trash' })
                }}
              />
            )}
            components={{
              Footer: () =>
                isFetchingNextPage ? (
                  <div className="flex justify-center py-6">
                    <span className="animate-spin h-5 w-5 border-2 border-[--color-text-muted] border-t-transparent rounded-full" />
                  </div>
                ) : null,
            }}
          />
        )}
      </div>

      {/* ── Mounted MediaViewer for Fullscreen Previews ── */}
      <MediaViewer allIds={allIds} />
    </div>
  )
}

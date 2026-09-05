import { useEffect, useCallback, useState, useRef } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  X,
  ChevronLeft,
  ChevronRight,
  Heart,
  Trash2,
  Info,
  Download,
  Smartphone,
  Laptop,
  Globe,
  Maximize2,
  Minimize2,
  Calendar,
  Camera,
  HardDrive,
  MapPin,
  Film,
} from 'lucide-react'
import { useMediaDetail, useFavoriteMedia, useDeleteMedia } from '@/hooks/useMedia'
import { getMediaURL, downloadMedia } from '@/api/client'
import { useUIStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { formatBytes, formatDate, isVideo, cn } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { Media } from '@/types'

function getDeviceIcon(type?: string) {
  switch (type) {
    case 'ios':
    case 'android':
      return <Smartphone size={12} />
    case 'mac':
      return <Laptop size={12} />
    default:
      return <Globe size={12} />
  }
}

// ─── Metadata panel ───────────────────────────────────────────────────────────

function MetadataRow({
  icon,
  label,
  value,
}: {
  icon?: React.ReactNode
  label: string
  value: string
}) {
  return (
    <div className="flex items-start gap-2.5 py-1.5 border-b border-white/5 last:border-0">
      {icon && <span className="text-white/40 mt-0.5 flex-shrink-0">{icon}</span>}
      <div className="flex flex-col min-w-0 flex-1">
        <span className="text-[10px] uppercase font-semibold tracking-wider text-white/40">{label}</span>
        <span className="text-xs text-white/90 leading-snug break-all font-medium">{value}</span>
      </div>
    </div>
  )
}

function MetadataPanel({
  media,
  onDelete,
  onClose,
}: {
  media: Media
  onDelete?: () => void
  onClose: () => void
}) {
  const { session } = useAuthStore()
  const isFromThisDevice = session?.deviceId && media.UploadedByDeviceID === session.deviceId

  const dateTaken = media.TakenAt ? new Date(media.TakenAt) : null

  return (
    <div className="w-80 flex-shrink-0 border-l border-white/10 bg-black/75 backdrop-blur-2xl p-5 overflow-y-auto h-full flex flex-col justify-between shadow-2xl z-30">
      <div className="space-y-4">
        <div className="flex items-center justify-between pb-2 border-b border-white/10">
          <h3 className="text-xs font-bold text-white uppercase tracking-widest flex items-center gap-2">
            <Info size={13} className="text-[--color-accent]" />
            Media Info
          </h3>
          <button
            onClick={onClose}
            className="text-white/50 hover:text-white p-1 rounded-full hover:bg-white/10 transition-colors"
          >
            <X size={14} />
          </button>
        </div>

        <div className="space-y-1">
          <MetadataRow label="File Name" value={media.Filename} />
          <MetadataRow
            icon={<HardDrive size={12} />}
            label="Source Device"
            value={isFromThisDevice ? `${media.UploadedByDeviceName || 'This Device'} (Local)` : (media.UploadedByDeviceName || 'Other device')}
          />
          {dateTaken && (
            <MetadataRow
              icon={<Calendar size={12} />}
              label="Capture Date (EXIF)"
              value={formatDate(dateTaken)}
            />
          )}
          <MetadataRow
            label="Upload Date"
            value={formatDate(new Date(media.UploadedAt))}
          />
          <MetadataRow label="File Size" value={formatBytes(media.SizeBytes)} />
          <MetadataRow label="Format / MIME" value={media.MIMEType} />
          {media.Width && media.Height && (
            <MetadataRow label="Resolution" value={`${media.Width} × ${media.Height} px`} />
          )}
          {media.DurationMS && (
            <MetadataRow
              label="Duration"
              value={`${(media.DurationMS / 1000).toFixed(1)}s`}
            />
          )}
          {(media.CameraMake ?? media.CameraModel) && (
            <MetadataRow
              icon={<Camera size={12} />}
              label="Camera"
              value={[media.CameraMake, media.CameraModel].filter(Boolean).join(' ')}
            />
          )}
          {media.GPSLat !== null && media.GPSLon !== null && (
            <MetadataRow
              icon={<MapPin size={12} />}
              label="Coordinates"
              value={`${media.GPSLat.toFixed(4)}, ${media.GPSLon.toFixed(4)}`}
            />
          )}
        </div>
      </div>

      <div className="pt-4 border-t border-white/10 flex flex-col gap-2 mt-4">
        <Button
          size="sm"
          variant="default"
          className="w-full gap-2 text-xs bg-white text-black hover:bg-white/90 font-medium"
          onClick={() => downloadMedia(media.FileID, media.Filename)}
        >
          <Download size={13} />
          Download Original
        </Button>
        {onDelete && (
          <Button
            size="sm"
            variant="destructive"
            className="w-full gap-2 text-xs font-medium"
            onClick={onDelete}
          >
            <Trash2 size={13} />
            Move to Trash
          </Button>
        )}
      </div>
    </div>
  )
}

// ─── Viewer image / video canvas ──────────────────────────────────────────────

function ViewerCanvas({
  media,
  isZoomed,
  onToggleZoom,
}: {
  media: Media
  isZoomed: boolean
  onToggleZoom: () => void
}) {
  const video = isVideo(media.MIMEType)
  const [highResLoaded, setHighResLoaded] = useState(false)
  const [thumbLoaded, setThumbLoaded] = useState(false)
  const [videoError, setVideoError] = useState(false)
  const [highResError, setHighResError] = useState(false)

  if (video) {
    const originalVideoUrl = getMediaURL(media.FileID, 'original')
    const previewVideoUrl = media.PreviewAvailable ? getMediaURL(media.FileID, 'preview') : originalVideoUrl
    const currentVideoSrc = videoError ? previewVideoUrl : originalVideoUrl

    return (
      <div className="relative w-full h-full flex items-center justify-center p-2 sm:p-6 overflow-hidden">
        <video
          key={currentVideoSrc}
          src={currentVideoSrc}
          controls
          autoPlay
          playsInline
          className="max-w-full max-h-full rounded-lg object-contain shadow-2xl transition-all"
          onError={() => {
            if (!videoError && media.PreviewAvailable) {
              setVideoError(true)
            }
          }}
        />
      </div>
    )
  }

  const hasThumbnail = media.ThumbnailAvailable
  const thumbUrl = hasThumbnail ? getMediaURL(media.FileID, 'thumbnail') : null
  const originalUrl = getMediaURL(media.FileID, 'original')

  return (
    <div
      className={cn(
        'relative w-full h-full flex items-center justify-center p-2 sm:p-4 transition-all duration-200 select-none',
        isZoomed ? 'overflow-auto cursor-zoom-out' : 'overflow-hidden cursor-zoom-in',
      )}
      onClick={onToggleZoom}
    >
      {/* ── Stage 1: Low-quality thumbnail loaded immediately with 0 lag ── */}
      {thumbUrl && (
        <img
          src={thumbUrl}
          alt={media.Filename}
          aria-hidden={highResLoaded}
          className={cn(
            'rounded-lg shadow-2xl transition-opacity duration-300 select-none',
            isZoomed
              ? 'max-w-none max-h-none object-none'
              : 'max-w-full max-h-full w-auto h-auto object-contain',
            thumbLoaded ? 'opacity-100' : 'opacity-0',
            // In zoom mode with HD loaded, hide thumbnail so scroll dimensions perfectly match HD image
            isZoomed && highResLoaded ? 'hidden' : 'block',
          )}
          draggable={false}
          onLoad={() => setThumbLoaded(true)}
        />
      )}

      {/* ── Stage 2: High-resolution original loaded in background and cross-fades smoothly ── */}
      <img
        src={originalUrl}
        alt={media.Filename}
        className={cn(
          'rounded-lg shadow-2xl transition-opacity duration-700 ease-in-out select-none',
          thumbUrl && !isZoomed ? 'absolute inset-0 m-auto' : 'relative',
          isZoomed
            ? 'max-w-none max-h-none object-none'
            : 'max-w-full max-h-full w-auto h-auto object-contain',
          highResLoaded ? 'opacity-100 z-10' : 'opacity-0 pointer-events-none z-0',
        )}
        draggable={false}
        loading="eager"
        decoding="async"
        onLoad={() => setHighResLoaded(true)}
        onError={() => setHighResError(true)}
      />

      {/* Subtle indicator showing that high-res is progressively streaming in without blocking view */}
      {!highResLoaded && !highResError && (thumbLoaded || !thumbUrl) && (
        <div className="absolute bottom-4 right-4 z-20 pointer-events-none flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/60 backdrop-blur-md border border-white/15 text-[11px] text-white/80 font-medium shadow-xl transition-opacity duration-300">
          <span className="w-1.5 h-1.5 rounded-full bg-[--color-accent] animate-ping" />
          <span>HD Loading…</span>
        </div>
      )}
    </div>
  )
}

// ─── Filmstrip Thumbnail Carousel ─────────────────────────────────────────────

function FilmstripCarousel({
  allIds,
  currentId,
  onSelect,
}: {
  allIds: string[]
  currentId: string
  onSelect: (id: string) => void
}) {
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!scrollRef.current) return
    const activeEl = scrollRef.current.querySelector(`[data-id="${currentId}"]`) as HTMLElement
    if (activeEl) {
      activeEl.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
    }
  }, [currentId])

  if (allIds.length <= 1) return null

  return (
    <div className="flex items-center gap-1.5 px-3 py-1.5 bg-black/60 backdrop-blur-xl border border-white/10 rounded-full max-w-[85vw] sm:max-w-xl overflow-x-auto scrollbar-none shadow-2xl">
      {allIds.map((id) => {
        const isCurrent = id === currentId
        const thumbUrl = getMediaURL(id, 'thumbnail')
        return (
          <button
            key={id}
            data-id={id}
            onClick={(e) => {
              e.stopPropagation()
              onSelect(id)
            }}
            className={cn(
              'w-9 h-9 flex-shrink-0 rounded-md overflow-hidden transition-all duration-150 relative cursor-pointer',
              isCurrent
                ? 'ring-2 ring-white scale-105 shadow-md'
                : 'opacity-50 hover:opacity-100 hover:scale-100',
            )}
          >
            <img
              src={thumbUrl}
              alt=""
              className="w-full h-full object-cover"
              loading="lazy"
            />
          </button>
        )
      })}
    </div>
  )
}

// ─── Main viewer modal ────────────────────────────────────────────────────────

interface MediaViewerProps {
  allIds?: string[]
}

export function MediaViewer({ allIds = [] }: MediaViewerProps) {
  const { session } = useAuthStore()
  const { viewerMediaId, closeViewer, openViewer, addToast } = useUIStore()
  const { data: media } = useMediaDetail(viewerMediaId)
  const favoriteMedia = useFavoriteMedia()
  const deleteMedia = useDeleteMedia()
  const [showInfo, setShowInfo] = useState(false)
  const [isZoomed, setIsZoomed] = useState(false)
  const [showFilmstrip, setShowFilmstrip] = useState(true)

  const currentIndex = viewerMediaId ? allIds.indexOf(viewerMediaId) : -1

  // Reset zoom on item switch
  useEffect(() => {
    setIsZoomed(false)
  }, [viewerMediaId])

  // Prefetch adjacent images for instant prev/next navigation.
  // Immediately caches adjacent thumbnails for 0-latency browsing, and preloads originals in background.
  useEffect(() => {
    if (!viewerMediaId || allIds.length <= 1) return
    const prefetchIds = [
      currentIndex > 0 ? allIds[currentIndex - 1] : null,
      currentIndex < allIds.length - 1 ? allIds[currentIndex + 1] : null,
    ].filter((id): id is string => id !== null)

    // 1. Immediately preload adjacent low-quality thumbnails into browser cache
    prefetchIds.forEach((id) => {
      const img = new Image()
      img.src = getMediaURL(id, 'thumbnail')
    })

    // 2. Prefetch adjacent originals at low network priority
    const links: HTMLLinkElement[] = []
    prefetchIds.forEach((id) => {
      const link = document.createElement('link')
      link.rel = 'prefetch'
      link.href = getMediaURL(id, 'original')
      link.as = 'image'
      document.head.appendChild(link)
      links.push(link)
    })
    return () => {
      links.forEach((link) => {
        if (document.head.contains(link)) {
          document.head.removeChild(link)
        }
      })
    }
  }, [currentIndex, allIds, viewerMediaId])

  const goNext = useCallback(() => {
    if (currentIndex < allIds.length - 1) openViewer(allIds[currentIndex + 1])
  }, [currentIndex, allIds, openViewer])

  const goPrev = useCallback(() => {
    if (currentIndex > 0) openViewer(allIds[currentIndex - 1])
  }, [currentIndex, allIds, openViewer])

  // Keyboard navigation
  useEffect(() => {
    if (!viewerMediaId) return
    const handleKey = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'Escape':
          closeViewer()
          break
        case 'ArrowRight':
          goNext()
          break
        case 'ArrowLeft':
          goPrev()
          break
        case 'i':
        case 'I':
          setShowInfo((s) => !s)
          break
        case 'z':
        case 'Z':
          setIsZoomed((z) => !z)
          break
        case 't':
        case 'T':
          setShowFilmstrip((f) => !f)
          break
        case 'd':
        case 'D':
          if (media) {
            downloadMedia(media.FileID, media.Filename)
            addToast({ type: 'info', message: `Downloading ${media.Filename}` })
          }
          break
      }
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [viewerMediaId, closeViewer, goNext, goPrev, media, addToast])

  const handleFavorite = async () => {
    if (!media) return
    await favoriteMedia.mutateAsync({ id: media.FileID, favorite: !media.Favorite })
    addToast({
      type: 'success',
      message: media.Favorite ? 'Removed from favorites' : 'Added to favorites',
    })
  }

  const handleDelete = async () => {
    if (!media) return
    await deleteMedia.mutateAsync(media.FileID)
    addToast({ type: 'success', message: 'Moved to trash' })
    closeViewer()
  }

  const handleDownload = () => {
    if (!media) return
    downloadMedia(media.FileID, media.Filename)
    addToast({ type: 'info', message: `Downloading ${media.Filename}` })
  }

  const isFromThisDevice = session?.deviceId && media?.UploadedByDeviceID === session.deviceId

  return (
    <AnimatePresence>
      {viewerMediaId && (
        <motion.div
          key="viewer"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          className="fixed inset-0 z-50 flex flex-col bg-black/85 backdrop-blur-2xl select-none"
        >
          {/* ── Top Floating Pill Toolbar ── */}
          <div className="absolute top-4 inset-x-4 z-40 flex items-center justify-between pointer-events-none">
            {/* Left: Close button + Filename info pill */}
            <div className="flex items-center gap-2 pointer-events-auto">
              <button
                onClick={closeViewer}
                className="w-10 h-10 rounded-full bg-black/60 hover:bg-black/80 backdrop-blur-xl border border-white/15 text-white flex items-center justify-center transition-all hover:scale-105 active:scale-95 shadow-xl cursor-pointer"
                aria-label="Close"
              >
                <X size={18} />
              </button>

              {media && (
                <div className="hidden sm:flex items-center gap-2.5 px-3.5 h-10 rounded-full bg-black/60 backdrop-blur-xl border border-white/15 shadow-xl text-white">
                  <span className="text-xs font-semibold max-w-[200px] truncate">
                    {media.Filename}
                  </span>
                  <div className="flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full bg-white/10 text-white/80 font-medium">
                    {getDeviceIcon(media.UploadedByDeviceType)}
                    <span className="max-w-[120px] truncate">
                      {isFromThisDevice ? 'This device' : (media.UploadedByDeviceName || 'Other device')}
                    </span>
                  </div>
                </div>
              )}
            </div>

            {/* Right: Actions Pill */}
            {media && (
              <div className="flex items-center gap-1 p-1 rounded-full bg-black/60 backdrop-blur-xl border border-white/15 shadow-xl pointer-events-auto">
                {!isVideo(media.MIMEType) && (
                  <button
                    onClick={() => setIsZoomed((z) => !z)}
                    className={cn(
                      'w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-white/15',
                      isZoomed && 'text-[--color-accent] bg-white/15',
                    )}
                    title={isZoomed ? 'Fit to screen (Z)' : 'Actual size (Z)'}
                  >
                    {isZoomed ? <Minimize2 size={15} /> : <Maximize2 size={15} />}
                  </button>
                )}

                {allIds.length > 1 && (
                  <button
                    onClick={() => setShowFilmstrip((s) => !s)}
                    className={cn(
                      'w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-white/15',
                      showFilmstrip && 'text-[--color-accent] bg-white/15',
                    )}
                    title={showFilmstrip ? 'Hide filmstrip (T)' : 'Show filmstrip (T)'}
                  >
                    <Film size={15} />
                  </button>
                )}

                <button
                  onClick={handleFavorite}
                  className="w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-white/15"
                  title={media.Favorite ? 'Remove from favorites' : 'Favorite'}
                >
                  <Heart
                    size={15}
                    className={media.Favorite ? 'fill-rose-500 text-rose-500' : 'text-white'}
                  />
                </button>

                <button
                  onClick={() => setShowInfo((s) => !s)}
                  className={cn(
                    'w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-white/15',
                    showInfo && 'text-[--color-accent] bg-white/15',
                  )}
                  title="Photo Info (I)"
                >
                  <Info size={15} />
                </button>

                <button
                  onClick={handleDownload}
                  className="w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-white/15"
                  title="Download file (D)"
                >
                  <Download size={15} />
                </button>

                <button
                  onClick={handleDelete}
                  className="w-8 h-8 rounded-full flex items-center justify-center transition-colors text-white hover:bg-rose-600/60 hover:text-rose-200"
                  title="Move to trash"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            )}
          </div>

          {/* ── Main Media Display Canvas (padded so controls & filmstrip never overlap) ── */}
          <div
            className={cn(
              'flex flex-1 overflow-hidden relative items-center justify-center pt-16 px-4 sm:px-14 transition-all duration-300',
              showFilmstrip && allIds.length > 1 ? 'pb-28' : 'pb-6',
            )}
          >
            {/* Prev Arrow */}
            {currentIndex > 0 && (
              <div className="absolute left-4 z-30">
                <button
                  onClick={goPrev}
                  className="w-11 h-11 rounded-full bg-black/60 hover:bg-black/85 backdrop-blur-xl border border-white/15 flex items-center justify-center text-white cursor-pointer transition-all hover:scale-110 active:scale-95 shadow-2xl"
                  aria-label="Previous (Left Arrow)"
                  title="Previous (Left Arrow)"
                >
                  <ChevronLeft size={24} />
                </button>
              </div>
            )}

            {/* Media Canvas */}
            <div className="w-full h-full flex items-center justify-center">
              {media && (
                <ViewerCanvas
                  key={media.FileID}
                  media={media}
                  isZoomed={isZoomed}
                  onToggleZoom={() => {
                    if (!isVideo(media.MIMEType)) setIsZoomed((z) => !z)
                  }}
                />
              )}
            </div>

            {/* Next Arrow */}
            {currentIndex < allIds.length - 1 && (
              <div className="absolute right-4 z-30">
                <button
                  onClick={goNext}
                  className="w-11 h-11 rounded-full bg-black/60 hover:bg-black/85 backdrop-blur-xl border border-white/15 flex items-center justify-center text-white cursor-pointer transition-all hover:scale-110 active:scale-95 shadow-2xl"
                  aria-label="Next (Right Arrow)"
                  title="Next (Right Arrow)"
                >
                  <ChevronRight size={24} />
                </button>
              </div>
            )}

            {/* Slide-in Info Drawer */}
            <AnimatePresence>
              {showInfo && media && (
                <motion.div
                  key="info"
                  initial={{ opacity: 0, x: 50 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 50 }}
                  transition={{ type: 'spring', bounce: 0, duration: 0.25 }}
                  className="absolute right-0 top-0 bottom-0 z-40 h-full"
                >
                  <MetadataPanel
                    media={media}
                    onDelete={handleDelete}
                    onClose={() => setShowInfo(false)}
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* ── Bottom Floating Carousel & Counter ── */}
          <AnimatePresence>
            {showFilmstrip && allIds.length > 1 && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 20 }}
                transition={{ duration: 0.15 }}
                className="absolute bottom-4 inset-x-4 z-30 flex flex-col items-center gap-2 pointer-events-none"
              >
                <div className="pointer-events-auto flex flex-col items-center gap-1.5">
                  <FilmstripCarousel
                    allIds={allIds}
                    currentId={viewerMediaId}
                    onSelect={openViewer}
                  />
                  <span className="text-[11px] font-mono text-white/60 bg-black/60 backdrop-blur-md px-2.5 py-0.5 rounded-full border border-white/10 shadow-md">
                    {currentIndex + 1} of {allIds.length}
                  </span>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

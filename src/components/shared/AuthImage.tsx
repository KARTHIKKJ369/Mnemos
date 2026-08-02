import { useState, useEffect } from 'react'
import { fetchMediaBlob } from '@/api/client'
import { blobCache, makeBlobCacheKey } from '@/lib/blobCache'
import { cn } from '@/lib/utils'

interface AuthImageProps {
  mediaId: string
  type: 'thumbnail' | 'preview' | 'original'
  alt: string
  className?: string
  onLoad?: () => void
  placeholder?: React.ReactNode
}

/**
 * Renders an auth-gated media image using a shared LRU blob URL cache.
 *
 * - First render: checks cache, shows skeleton if miss, fetches in background
 * - Cache hit: renders immediately, no flash
 * - URLs are NOT revoked on unmount — the LRU cache manages their lifetime
 * - Max 400 cached URLs; LRU eviction automatically revokes old ones
 */
export function AuthImage({
  mediaId,
  type,
  alt,
  className,
  onLoad,
  placeholder,
}: AuthImageProps) {
  const cacheKey = makeBlobCacheKey(mediaId, type)

  const [src, setSrc] = useState<string | null>(() => blobCache.get(cacheKey) ?? null)
  const [loaded, setLoaded] = useState(() => blobCache.has(cacheKey))
  const [error, setError] = useState(false)

  useEffect(() => {
    const key = makeBlobCacheKey(mediaId, type)

    // Cache hit — nothing to do
    const cached = blobCache.get(key)
    if (cached) {
      setSrc(cached)
      setLoaded(true)
      return
    }

    let cancelled = false
    setSrc(null)
    setLoaded(false)
    setError(false)

    fetchMediaBlob(mediaId, type)
      .then((objectUrl) => {
        if (cancelled) {
          // Component unmounted before fetch finished — don't cache, revoke immediately
          URL.revokeObjectURL(objectUrl)
          return
        }
        blobCache.set(key, objectUrl)
        setSrc(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })

    return () => {
      cancelled = true
      // Do NOT revoke here — the cache owns the URL lifetime
    }
  }, [mediaId, type])

  if (error) {
    return (
      <div className={cn('flex items-center justify-center bg-[--color-surface-overlay]', className)}>
        <span className="text-[--color-text-disabled] text-xs">—</span>
      </div>
    )
  }

  return (
    <div className={cn('relative', className)}>
      {/* Skeleton shown while loading */}
      {!loaded && (
        <div className={cn('absolute inset-0 skeleton', !src && 'z-10')}>
          {placeholder}
        </div>
      )}
      {src && (
        <img
          src={src}
          alt={alt}
          className={cn(
            'w-full h-full object-cover',
            'transition-opacity duration-[200ms] ease-out',
            loaded ? 'opacity-100' : 'opacity-0',
          )}
          onLoad={() => {
            setLoaded(true)
            onLoad?.()
          }}
        />
      )}
    </div>
  )
}

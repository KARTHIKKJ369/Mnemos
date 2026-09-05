import { useState } from 'react'
import { getMediaURL } from '@/api/client'
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
 * Renders an authenticated media image using a direct streaming URL with the
 * auth token as a query parameter. This avoids the previous pattern of
 * downloading the full file into JavaScript memory as a Blob object URL, which
 * caused:
 *  - Full video files being buffered in RAM when falling back to "original"
 *  - HTTP connection pool exhaustion (6 connections limit) blocking all other requests
 *  - Slow gallery scrolling due to unthrottled parallel fetches
 *
 * The browser handles lazy loading, caching, request cancellation, and streaming
 * natively with <img loading="lazy">.
 */
export function AuthImage({
  mediaId,
  type,
  alt,
  className,
  onLoad,
  placeholder,
}: AuthImageProps) {
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)

  const src = getMediaURL(mediaId, type)

  if (error) {
    return (
      <div className={cn('flex items-center justify-center bg-[--color-surface-overlay]', className)}>
        <span className="text-[--color-text-disabled] text-xs">Failed</span>
      </div>
    )
  }

  return (
    <>
      {!loaded && (
        <div className={cn('skeleton', className)}>
          {placeholder}
        </div>
      )}
      <img
        src={src}
        alt={alt}
        className={cn(
          'transition-opacity duration-[220ms] ease-out',
          loaded ? 'opacity-100' : 'opacity-0 absolute',
          className,
        )}
        loading="lazy"
        decoding="async"
        onLoad={() => {
          setLoaded(true)
          onLoad?.()
        }}
        onError={() => setError(true)}
      />
    </>
  )
}

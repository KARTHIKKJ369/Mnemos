import { useState, useEffect, useRef } from 'react'
import { fetchMediaBlob } from '@/api/client'
import { cn } from '@/lib/utils'

interface AuthImageProps {
  mediaId: string
  type: 'thumbnail' | 'preview' | 'original'
  alt: string
  className?: string
  onLoad?: () => void
  placeholder?: React.ReactNode
}

/** Fetches media with the auth token and renders it as an <img>.
 *  Cleans up the object URL on unmount to prevent memory leaks. */
export function AuthImage({
  mediaId,
  type,
  alt,
  className,
  onLoad,
  placeholder,
}: AuthImageProps) {
  const [src, setSrc] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)
  const urlRef = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setSrc(null)
    setLoaded(false)
    setError(false)

    fetchMediaBlob(mediaId, type)
      .then((objectUrl) => {
        if (cancelled) {
          URL.revokeObjectURL(objectUrl)
          return
        }
        // Revoke old URL
        if (urlRef.current) URL.revokeObjectURL(urlRef.current)
        urlRef.current = objectUrl
        setSrc(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })

    return () => {
      cancelled = true
    }
  }, [mediaId, type])

  useEffect(() => {
    return () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current)
    }
  }, [])

  if (error) {
    return (
      <div className={cn('flex items-center justify-center bg-[--color-surface-overlay]', className)}>
        <span className="text-[--color-text-disabled] text-xs">Failed</span>
      </div>
    )
  }

  return (
    <>
      {(!src || !loaded) && (
        <div className={cn('skeleton', className)}>
          {placeholder}
        </div>
      )}
      {src && (
        <img
          src={src}
          alt={alt}
          className={cn(
            'transition-opacity duration-[220ms] ease-out',
            loaded ? 'opacity-100' : 'opacity-0 absolute',
            className,
          )}
          onLoad={() => {
            setLoaded(true)
            onLoad?.()
          }}
        />
      )}
    </>
  )
}


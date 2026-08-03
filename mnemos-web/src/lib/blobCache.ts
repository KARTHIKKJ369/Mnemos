/**
 * LRU Blob URL cache — manages object URL lifecycle across the app.
 *
 * Rules:
 * - Entries are keyed by `${mediaId}:${type}`
 * - Max 400 entries before LRU eviction (oldest evicted + URL revoked)
 * - Never revoke on component unmount — the cache owns the lifetime
 * - Map insertion order = LRU order (Map preserves insertion, we re-insert on access)
 */

class BlobURLCache {
  private readonly cache = new Map<string, string>()
  private readonly maxSize: number

  constructor(maxSize = 400) {
    this.maxSize = maxSize
  }

  get(key: string): string | undefined {
    const value = this.cache.get(key)
    if (value !== undefined) {
      // Promote to most-recently-used by re-inserting at tail
      this.cache.delete(key)
      this.cache.set(key, value)
    }
    return value
  }

  set(key: string, url: string): void {
    if (this.cache.has(key)) {
      // Update value, promote to tail
      this.cache.delete(key)
    } else if (this.cache.size >= this.maxSize) {
      // Evict LRU (first entry in Map)
      const firstKey = this.cache.keys().next().value
      if (firstKey !== undefined) {
        const evictedUrl = this.cache.get(firstKey)!
        URL.revokeObjectURL(evictedUrl)
        this.cache.delete(firstKey)
      }
    }
    this.cache.set(key, url)
  }

  has(key: string): boolean {
    return this.cache.has(key)
  }

  invalidate(key: string): void {
    const url = this.cache.get(key)
    if (url !== undefined) {
      URL.revokeObjectURL(url)
      this.cache.delete(key)
    }
  }

  invalidateMedia(mediaId: string): void {
    for (const type of ['thumbnail', 'preview', 'original'] as const) {
      this.invalidate(`${mediaId}:${type}`)
    }
  }

  clear(): void {
    this.cache.forEach((url) => URL.revokeObjectURL(url))
    this.cache.clear()
  }

  get size(): number {
    return this.cache.size
  }
}

/** Singleton shared across the entire app */
export const blobCache = new BlobURLCache(400)

export function makeBlobCacheKey(mediaId: string, type: 'thumbnail' | 'preview' | 'original'): string {
  return `${mediaId}:${type}`
}

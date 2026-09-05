import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { Media } from '@/types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export function formatMonthYear(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

export function formatDate(date: Date): string {
  return date.toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric' })
}

export function formatRelative(date: Date): string {
  const now = Date.now()
  const diff = now - date.getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  return formatDate(date)
}

export function mediaDate(takenAt: string | null, uploadedAt: string): Date {
  return takenAt ? new Date(takenAt) : new Date(uploadedAt)
}

export function formatTimelineDate(date: Date): string {
  const now = new Date()
  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear()
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const isYesterday =
    date.getDate() === yesterday.getDate() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getFullYear() === yesterday.getFullYear()

  if (isToday) return 'Today'
  if (isYesterday) return 'Yesterday'

  const isThisYear = date.getFullYear() === now.getFullYear()
  if (isThisYear) {
    return date.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
    })
  }
  return date.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

export function groupByDay(
  items: Media[],
): Array<{ label: string; subLabel: string; date: Date; items: Media[] }> {
  const groups = new Map<string, { label: string; subLabel: string; date: Date; items: Media[] }>()
  for (const item of items) {
    const date = mediaDate(item.TakenAt, item.UploadedAt)
    const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
    if (!groups.has(key)) {
      groups.set(key, {
        label: formatTimelineDate(date),
        subLabel: date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
        date,
        items: [],
      })
    }
    groups.get(key)!.items.push(item)
  }
  return Array.from(groups.values()).sort((a, b) => b.date.getTime() - a.date.getTime())
}

export function groupByMonth(
  items: Media[],
): Array<{ label: string; subLabel: string; date: Date; items: Media[] }> {
  const groups = new Map<string, { label: string; subLabel: string; date: Date; items: Media[] }>()
  for (const item of items) {
    const date = mediaDate(item.TakenAt, item.UploadedAt)
    const key = `${date.getFullYear()}-${date.getMonth()}`
    if (!groups.has(key)) {
      groups.set(key, {
        label: formatMonthYear(date),
        subLabel: `${date.getFullYear()}`,
        date,
        items: [],
      })
    }
    groups.get(key)!.items.push(item)
  }
  return Array.from(groups.values()).sort((a, b) => b.date.getTime() - a.date.getTime())
}


export function isVideo(mime: string): boolean {
  return mime.startsWith('video/')
}

export function isImage(mime: string): boolean {
  return mime.startsWith('image/')
}

export async function hashFile(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}


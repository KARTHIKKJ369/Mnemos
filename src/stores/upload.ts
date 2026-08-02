import { create } from 'zustand'
import { v4 as uuidv4 } from 'uuid'
import type { UploadItem, UploadStatus } from '@/types'

interface UploadStore {
  queue: UploadItem[]
  isOpen: boolean
  addFiles: (files: File[]) => void
  updateItem: (id: string, patch: Partial<UploadItem>) => void
  removeItem: (id: string) => void
  clearCompleted: () => void
  setOpen: (open: boolean) => void
  activeCount: () => number
}

const TERMINAL_STATUSES: UploadStatus[] = ['complete', 'duplicate', 'error', 'cancelled']

export const useUploadStore = create<UploadStore>()((set, get) => ({
  queue: [],
  isOpen: false,

  addFiles: (files) => {
    const items: UploadItem[] = files.map((file) => ({
      id: uuidv4(),
      file,
      status: 'hashing',
      progress: 0,
    }))
    set((s) => ({ queue: [...s.queue, ...items], isOpen: true }))
  },

  updateItem: (id, patch) => {
    set((s) => ({
      queue: s.queue.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    }))
  },

  removeItem: (id) => {
    set((s) => ({ queue: s.queue.filter((item) => item.id !== id) }))
  },

  clearCompleted: () => {
    set((s) => ({
      queue: s.queue.filter((item) => !TERMINAL_STATUSES.includes(item.status)),
    }))
  },

  setOpen: (open) => set({ isOpen: open }),

  activeCount: () => {
    const { queue } = get()
    return queue.filter((i) => !TERMINAL_STATUSES.includes(i.status)).length
  },
}))

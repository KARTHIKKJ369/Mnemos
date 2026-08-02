import { create } from 'zustand'
import type { GalleryViewMode } from '@/types'

interface UIStore {
  // Viewer
  viewerMediaId: string | null
  openViewer: (id: string) => void
  closeViewer: () => void

  // Gallery selection
  selectedIds: Set<string>
  isSelectMode: boolean
  toggleSelectMode: () => void
  toggleSelect: (id: string) => void
  selectAll: (ids: string[]) => void
  clearSelection: () => void

  // Gallery view
  viewMode: GalleryViewMode
  setViewMode: (mode: GalleryViewMode) => void

  // Toast
  toasts: Toast[]
  addToast: (toast: Omit<Toast, 'id'>) => void
  removeToast: (id: string) => void
}

export interface Toast {
  id: string
  message: string
  type: 'success' | 'error' | 'info'
}

let toastCounter = 0

export const useUIStore = create<UIStore>()((set) => ({
  // Viewer
  viewerMediaId: null,
  openViewer: (id) => set({ viewerMediaId: id }),
  closeViewer: () => set({ viewerMediaId: null }),

  // Selection
  selectedIds: new Set(),
  isSelectMode: false,
  toggleSelectMode: () =>
    set((s) => ({
      isSelectMode: !s.isSelectMode,
      selectedIds: new Set(),
    })),
  toggleSelect: (id) =>
    set((s) => {
      const next = new Set(s.selectedIds)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return { selectedIds: next }
    }),
  selectAll: (ids) => set({ selectedIds: new Set(ids) }),
  clearSelection: () => set({ selectedIds: new Set(), isSelectMode: false }),

  // View mode
  viewMode: 'timeline',
  setViewMode: (mode) => set({ viewMode: mode }),

  // Toasts
  toasts: [],
  addToast: (toast) => {
    const id = String(++toastCounter)
    set((s) => ({ toasts: [...s.toasts, { ...toast, id }] }))
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
    }, 3500)
  },
  removeToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))


import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

interface StorageStore {
  /** ID of the library selected as the upload destination. null = use server default. */
  selectedLibraryId: string | null
  setSelectedLibraryId: (id: string | null) => void
}

export const useStorageStore = create<StorageStore>()(
  persist(
    (set) => ({
      selectedLibraryId: null,
      setSelectedLibraryId: (id) => set({ selectedLibraryId: id }),
    }),
    {
      name: 'mnemos_storage_prefs',
      storage: createJSONStorage(() => localStorage),
    },
  ),
)

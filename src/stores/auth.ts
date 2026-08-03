import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { AuthSession } from '@/types'

interface AuthStore {
  session: AuthSession | null
  setSession: (session: AuthSession) => void
  clearSession: () => void
  isAuthenticated: boolean
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      session: null,
      isAuthenticated: false,
      setSession: (session) => {
        set({ session, isAuthenticated: true })
      },
      clearSession: () => {
        set({ session: null, isAuthenticated: false })
        localStorage.removeItem('mnemos_session')
      },
    }),
    {
      name: 'mnemos_session',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state) => {
        if (state?.session) state.isAuthenticated = true
      },
    },
  ),
)


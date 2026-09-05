import { useEffect } from 'react'
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { AuthPage } from '@/features/auth/AuthPage'
import { useAuthStore } from '@/stores/auth'
import { getAuthBootstrap } from '@/api/client'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated, session, setSession } = useAuthStore()

  // Only refresh the session for already-authenticated ADMIN users (host Mac Mini).
  //
  // Non-admin clients (Arch laptop, phone, etc.) have stable device registration
  // tokens that don't change. Calling bootstrap for them is dangerous because
  // all traffic goes through the Vite dev proxy (127.0.0.1:5173 → 127.0.0.1:8080).
  // The Go server sees every request as loopback and returns is_admin: true + the
  // admin token — which would overwrite the non-admin session and give remote
  // clients the Mac Mini's device ID, breaking "From This Device" filtering.
  //
  // Unauthenticated users are handled by AuthPage.tsx (shows bootstrap → admin
  // auto-login on loopback, device form on remote).
  useEffect(() => {
    if (!session?.isAdmin) return   // non-admin or not authenticated → skip

    let cancelled = false

    async function refreshAdminToken() {
      try {
        const res = await getAuthBootstrap()
        if (cancelled) return

        if (res.is_admin && res.auth_token && res.device_id) {
          setSession({
            deviceId: res.device_id,
            authToken: res.auth_token,
            deviceName: res.device_name || 'Server Host (Admin)',
            serverUrl: window.location.origin,
            isAdmin: true,
          })
        }
      } catch {
        // Offline or error — leave existing admin session intact
      }
    }

    refreshAdminToken()
    return () => {
      cancelled = true
    }
  // Re-run only when isAdmin changes (e.g., first admin login)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.isAdmin])

  if (!isAuthenticated || !session) {
    return <AuthPage />
  }

  return <Outlet />
}



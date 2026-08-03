import { createRootRoute, Outlet } from '@tanstack/react-router'
import { AuthPage } from '@/features/auth/AuthPage'
import { useAuthStore } from '@/stores/auth'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated } = useAuthStore()

  if (!isAuthenticated) {
    return <AuthPage />
  }

  return <Outlet />
}


import { createFileRoute } from '@tanstack/react-router'
import { SyncPage } from '@/features/sync/SyncPage'

export const Route = createFileRoute('/_app/sync')({
  component: SyncPage,
})


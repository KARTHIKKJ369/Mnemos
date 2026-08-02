import { createFileRoute } from '@tanstack/react-router'
import { VaultsPage } from '@/features/vaults/VaultsPage'

export const Route = createFileRoute('/_app/vaults')({
  component: VaultsPage,
})


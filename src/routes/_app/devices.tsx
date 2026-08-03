import { createFileRoute } from '@tanstack/react-router'
import { DevicesPage } from '@/features/devices/DevicesPage'

export const Route = createFileRoute('/_app/devices')({
  component: DevicesPage,
})


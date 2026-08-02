import { createFileRoute } from '@tanstack/react-router'
import { TimelinePage } from '@/features/gallery/TimelinePage'

export const Route = createFileRoute('/_app/timeline')({
  component: TimelinePage,
})


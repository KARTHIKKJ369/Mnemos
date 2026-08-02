import { createFileRoute } from '@tanstack/react-router'
import { GalleryPage } from '@/features/gallery/GalleryPage'

export const Route = createFileRoute('/_app/gallery')({
  component: GalleryPage,
})


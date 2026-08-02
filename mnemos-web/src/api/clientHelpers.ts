export function getBaseURL(): string {
  return (import.meta.env.VITE_API_URL as string | undefined) ?? '/api'
}


import { useState } from 'react'
import { Settings, LogOut } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getHealth } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest px-1">
        {title}
      </h2>
      <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] overflow-hidden">
        {children}
      </div>
    </div>
  )
}

function Row({
  label,
  value,
  mono,
  action,
}: {
  label: string
  value?: string
  mono?: boolean
  action?: React.ReactNode
}) {
  return (
    <div className="flex items-center gap-4 px-4 py-3 border-b border-[--color-border-subtle] last:border-0">
      <span className="text-xs text-[--color-text-muted] w-28 flex-shrink-0">{label}</span>
      {value && (
        <span className={`text-xs text-[--color-text-secondary] flex-1 ${mono ? 'font-mono' : ''} truncate`}>
          {value}
        </span>
      )}
      {action && <div className="ml-auto flex-shrink-0">{action}</div>}
    </div>
  )
}

export function SettingsPage() {
  const { session, clearSession } = useAuthStore()
  const { addToast } = useUIStore()
  const [confirming, setConfirming] = useState(false)

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: 1,
  })

  const handleLogout = () => {
    if (!confirming) {
      setConfirming(true)
      return
    }
    clearSession()
    addToast({ type: 'info', message: 'Session cleared' })
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-2 px-6 py-3 border-b border-[--color-border-subtle]">
        <Settings size={15} className="text-[--color-text-muted]" />
        <h1 className="text-sm font-semibold text-[--color-text-primary]">Settings</h1>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6 max-w-xl">
        {/* Connection */}
        <Section title="Connection">
          <Row label="Server URL" value={session?.serverUrl} mono />
          <Row
            label="Server status"
            value={health?.status === 'ok' ? 'Healthy' : 'Unreachable'}
            action={
              <span className={`w-2 h-2 rounded-full ${health?.status === 'ok' ? 'bg-[--color-success]' : 'bg-[--color-danger]'}`} />
            }
          />
        </Section>

        {/* Session */}
        <Section title="Session">
          <Row label="Device name" value={session?.deviceName} />
          <Row label="Device ID" value={session?.deviceId} mono />
          <Row
            label="Auth token"
            value="••••••••••••••••"
            mono
            action={
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  if (session?.authToken) {
                    navigator.clipboard.writeText(session.authToken)
                    addToast({ type: 'success', message: 'Token copied' })
                  }
                }}
              >
                Copy
              </Button>
            }
          />
        </Section>

        {/* About */}
        <Section title="About">
          <Row label="App" value="Mnemos" />
          <Row label="Backend" value="PhotoVault (Go)" />
          <Row
            label="API"
            value="v1 — device, upload, sync, media, vaults"
          />
        </Section>

        {/* Danger zone */}
        <Section title="Danger zone">
          <div className="px-4 py-3">
            <p className="text-xs text-[--color-text-muted] mb-3">
              Clearing the session removes the auth token from this browser.
              Your photos remain safe on the server. You'll need to register again to reconnect.
            </p>
            <Button
              variant={confirming ? 'destructive' : 'outline'}
              size="sm"
              onClick={handleLogout}
              onBlur={() => setConfirming(false)}
            >
              <LogOut size={12} />
              {confirming ? 'Click again to confirm' : 'Clear session'}
            </Button>
          </div>
        </Section>
      </div>
    </div>
  )
}


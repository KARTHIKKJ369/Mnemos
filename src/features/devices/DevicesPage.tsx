import { useState } from 'react'
import { motion } from 'motion/react'
import { Monitor, Globe, Plus } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getHealth, registerDevice } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUIStore } from '@/stores/ui'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

export function DevicesPage() {
  const { session } = useAuthStore()
  const { addToast } = useUIStore()
  const [showRegister, setShowRegister] = useState(false)
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<'web' | 'mac' | 'ios' | 'android'>('web')
  const [registering, setRegistering] = useState(false)
  const [newToken, setNewToken] = useState<string | null>(null)

  const { data: health, isError } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 30_000,
    retry: 2,
  })

  const handleRegisterNew = async () => {
    if (!newName.trim()) return
    setRegistering(true)
    try {
      const result = await registerDevice(newName.trim(), newType)
      setNewToken(result.auth_token)
      setNewName('')
      addToast({ type: 'success', message: 'Device registered — copy the token now!' })
      setShowRegister(false)
    } catch {
      addToast({ type: 'error', message: 'Registration failed' })
    } finally {
      setRegistering(false)
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <Monitor size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Devices</h1>
        </div>
        <Button size="sm" variant="ghost" onClick={() => setShowRegister((s) => !s)}>
          <Plus size={12} />
          Register new
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* Server status */}
        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Server</h2>
          <div className="flex items-center gap-3">
            <div className={`w-2 h-2 rounded-full ${isError ? 'bg-[--color-danger]' : 'bg-[--color-success]'}`} />
            <span className="text-sm text-[--color-text-primary]">{session?.serverUrl}</span>
            <span className={`text-xs ml-auto ${isError ? 'text-[--color-danger]' : 'text-[--color-success]'}`}>
              {isError ? 'Unreachable' : health?.status === 'ok' ? 'Healthy' : 'Checking…'}
            </span>
          </div>
        </div>

        {/* Current device */}
        {session && (
          <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3">
            <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">This device</h2>
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-[--radius-md] bg-[--color-surface-subtle] flex items-center justify-center text-[--color-text-secondary]">
                <Globe size={15} />
              </div>
              <div>
                <p className="text-sm text-[--color-text-primary]">{session.deviceName}</p>
                <p className="text-xs text-[--color-text-muted] font-mono">{session.deviceId.slice(0, 8)}…</p>
              </div>
              <span className="ml-auto text-xs bg-[--color-surface-subtle] text-[--color-text-muted] px-2 py-0.5 rounded-full">web</span>
            </div>
          </div>
        )}

        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-2">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Other devices</h2>
          <p className="text-xs text-[--color-text-muted]">
            The backend does not yet expose a <code className="text-[--color-text-secondary]">GET /devices</code> endpoint.
            Once added, all registered devices and their last-seen timestamps will appear here.
          </p>
        </div>

        {showRegister && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3 border border-[--color-border-default]"
          >
            <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">Register a device</h2>
            <p className="text-xs text-[--color-text-muted]">
              The auth token is shown <strong className="text-[--color-text-secondary]">only once</strong> — copy it immediately.
            </p>
            <div className="space-y-2">
              <Input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Device name (e.g. Karthik's iPhone)" />
              <select
                value={newType}
                onChange={(e) => setNewType(e.target.value as typeof newType)}
                className="w-full px-3 h-9 text-sm bg-[--color-surface-subtle] text-[--color-text-primary] border border-[--color-border-default] rounded-[--radius-md] focus:outline-none"
              >
                <option value="web">Web</option>
                <option value="mac">Mac</option>
                <option value="ios">iOS</option>
                <option value="android">Android</option>
              </select>
              <Button variant="accent" size="sm" className="w-full" onClick={handleRegisterNew} loading={registering} disabled={!newName.trim()}>
                Register
              </Button>
            </div>
          </motion.div>
        )}

        {newToken && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
            className="bg-[--color-danger-surface] border border-red-900 rounded-[--radius-lg] p-4 space-y-2"
          >
            <p className="text-xs font-semibold text-[--color-danger]">⚠ Copy this token now — it won't be shown again</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 text-xs text-[--color-text-secondary] bg-[--color-surface-base] px-3 py-2 rounded-[--radius-md] font-mono break-all select-all">
                {newToken}
              </code>
              <Button size="sm" variant="ghost" onClick={() => { navigator.clipboard.writeText(newToken); addToast({ type: 'success', message: 'Token copied!' }) }}>
                Copy
              </Button>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setNewToken(null)} className="w-full text-[--color-text-muted]">
              I've saved the token
            </Button>
          </motion.div>
        )}
      </div>
    </div>
  )
}


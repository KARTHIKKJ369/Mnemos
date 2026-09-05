import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import {
  Monitor,
  Globe,
  Plus,
  Smartphone,
  Laptop,
  CheckCircle2,
  Copy,
  Server,
  X,
  Trash2,
} from 'lucide-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getHealth, registerDevice, getDevices, deleteDevice } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUIStore } from '@/stores/ui'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { formatBytes } from '@/lib/utils'
import type { DeviceType } from '@/types'

function getDeviceIcon(type: DeviceType) {
  switch (type) {
    case 'ios':
    case 'android':
      return <Smartphone size={16} />
    case 'mac':
      return <Laptop size={16} />
    case 'web':
    default:
      return <Globe size={16} />
  }
}

function formatDate(isoString?: string) {
  if (!isoString) return 'Never'
  try {
    const d = new Date(isoString)
    const diffMin = Math.round((Date.now() - d.getTime()) / 60000)
    if (diffMin < 2) return 'Active now'
    if (diffMin < 60) return `${diffMin}m ago`
    if (diffMin < 1440) return `${Math.floor(diffMin / 60)}h ago`
    return d.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return isoString
  }
}

export function DevicesPage() {
  const { session } = useAuthStore()
  const { addToast } = useUIStore()
  const queryClient = useQueryClient()

  const [showRegisterModal, setShowRegisterModal] = useState(false)
  const [newName, setNewName] = useState('')
  const [newType, setNewType] = useState<DeviceType>('ios')
  const [registering, setRegistering] = useState(false)
  const [newRegistration, setNewRegistration] = useState<{ id: string; token: string } | null>(null)

  const { data: health, isError: serverError } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 15_000,
  })

  const { data: devicesData, isLoading: loadingDevices } = useQuery({
    queryKey: ['devices'],
    queryFn: getDevices,
    refetchInterval: 15_000,
  })

  const handleRegister = async () => {
    if (!newName.trim()) return
    setRegistering(true)
    try {
      const result = await registerDevice(newName.trim(), newType)
      setNewRegistration({ id: result.device_id, token: result.auth_token })
      setNewName('')
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      addToast({ type: 'success', message: 'Client device registered!' })
    } catch {
      addToast({ type: 'error', message: 'Registration failed' })
    } finally {
      setRegistering(false)
    }
  }

  const handleDeleteDevice = async (id: string, name: string) => {
    if (!window.confirm(`Are you sure you want to unregister and remove device "${name}"?`)) return
    try {
      await deleteDevice(id)
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      addToast({ type: 'success', message: `Device "${name}" removed` })
    } catch {
      addToast({ type: 'error', message: 'Failed to remove device' })
    }
  }

  const devices = devicesData?.devices ?? []

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle] bg-[--color-surface-base]">
        <div className="flex items-center gap-2">
          <Monitor size={16} className="text-[--color-text-muted]" />
          <h1 className="text-base font-bold text-[--color-text-primary] tracking-tight">Clients & Devices</h1>
        </div>
        <Button size="sm" variant="accent" onClick={() => setShowRegisterModal(true)} className="gap-1.5 font-semibold text-xs">
          <Plus size={13} />
          Add Client Device
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6 max-w-4xl">
        {/* ── Server Host Card ── */}
        <div className="bg-[--color-surface-overlay] border border-[--color-border-subtle] rounded-[--radius-xl] p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-[--radius-md] bg-[--color-accent]/10 text-[--color-accent] flex items-center justify-center">
                <Server size={17} />
              </div>
              <div>
                <h2 className="text-sm font-bold text-[--color-text-primary]">PhotoVault Server Host</h2>
                <p className="text-xs text-[--color-text-muted]">This device stores all media and syncs clients</p>
              </div>
            </div>
            <span
              className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-medium ${
                serverError
                  ? 'bg-red-950/40 text-red-400 border border-red-900/50'
                  : 'bg-emerald-950/40 text-emerald-400 border border-emerald-900/50'
              }`}
            >
              <span className={`w-1.5 h-1.5 rounded-full ${serverError ? 'bg-red-500' : 'bg-emerald-500 animate-pulse'}`} />
              {serverError ? 'Offline' : 'Online & Healthy'}
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2 border-t border-[--color-border-subtle]">
            <div className="p-3 bg-[--color-surface-base] rounded-[--radius-md] border border-[--color-border-subtle]/60">
              <span className="text-[11px] text-[--color-text-muted] block uppercase tracking-wider font-semibold">
                Server URL
              </span>
              <span className="text-xs font-mono font-medium text-[--color-text-primary] mt-1 block truncate">
                {session?.serverUrl || 'http://127.0.0.1:8080'}
              </span>
            </div>

            <div className="p-3 bg-[--color-surface-base] rounded-[--radius-md] border border-[--color-border-subtle]/60">
              <span className="text-[11px] text-[--color-text-muted] block uppercase tracking-wider font-semibold">
                {session?.isAdmin ? 'Storage Folder' : 'Mesh Status'}
              </span>
              {session?.isAdmin ? (
                <span className="text-xs font-mono font-medium text-[--color-text-primary] mt-1 block truncate" title={health?.storage_path}>
                  {health?.storage_path || 'storage'}
                </span>
              ) : (
                <span className="text-xs font-semibold text-emerald-400 mt-1 block">
                  Connected &amp; Healthy
                </span>
              )}
            </div>

            <div className="p-3 bg-[--color-surface-base] rounded-[--radius-md] border border-[--color-border-subtle]/60">
              <span className="text-[11px] text-[--color-text-muted] block uppercase tracking-wider font-semibold">
                Free Disk Space
              </span>
              <span className="text-xs font-mono font-medium text-[--color-accent] mt-1 block">
                {health?.disk_free_bytes ? formatBytes(health.disk_free_bytes) : 'Available'}
              </span>
            </div>
          </div>
        </div>

        {/* ── Connected Clients List ── */}
        <div className="space-y-3">
          <div className="flex items-center justify-between px-1">
            <div>
              <h2 className="text-sm font-bold text-[--color-text-primary]">Connected Client Devices</h2>
              <p className="text-xs text-[--color-text-muted]">
                Phones, tablets, and computers authorized to upload and download from this server
              </p>
            </div>
            <span className="text-xs text-[--color-text-muted] font-mono bg-[--color-surface-subtle] px-2 py-0.5 rounded-full">
              {devices.length} client{devices.length === 1 ? '' : 's'}
            </span>
          </div>

          {loadingDevices ? (
            <div className="space-y-2">
              <div className="h-16 bg-[--color-surface-overlay] rounded-[--radius-lg] animate-pulse" />
              <div className="h-16 bg-[--color-surface-overlay] rounded-[--radius-lg] animate-pulse" />
            </div>
          ) : devices.length === 0 ? (
            <div className="p-8 text-center bg-[--color-surface-overlay] rounded-[--radius-xl] border border-[--color-border-subtle]">
              <Smartphone size={24} className="mx-auto text-[--color-text-disabled] mb-2" />
              <p className="text-xs text-[--color-text-secondary]">No client devices registered yet</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {devices.map((device) => {
                const isCurrent = device.id === session?.deviceId
                return (
                  <div
                    key={device.id}
                    className={`flex items-center gap-3.5 p-4 rounded-[--radius-lg] border transition-all ${
                      isCurrent
                        ? 'bg-[--color-surface-subtle] border-[--color-accent]/40 shadow-xs'
                        : 'bg-[--color-surface-overlay] border-[--color-border-subtle]'
                    }`}
                  >
                    <div className="w-10 h-10 rounded-[--radius-md] bg-[--color-surface-base] border border-[--color-border-subtle] flex items-center justify-center text-[--color-text-primary]">
                      {getDeviceIcon(device.device_type)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-[--color-text-primary] truncate">
                          {device.name}
                        </span>
                        {isCurrent && (
                          <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-black bg-[--color-accent] px-1.5 py-0.2 rounded-full">
                            <CheckCircle2 size={10} />
                            This device
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 text-xs text-[--color-text-muted] mt-0.5">
                        <span className="uppercase text-[10px] tracking-wider font-semibold font-mono">
                          {device.device_type}
                        </span>
                        <span>•</span>
                        <span className="truncate">{formatDate(device.last_seen_at)}</span>
                      </div>
                    </div>
                    {!isCurrent && device.name !== 'Server Host (Admin)' && (
                      <button
                        onClick={() => handleDeleteDevice(device.id, device.name)}
                        className="p-2 text-[--color-text-muted] hover:text-rose-400 hover:bg-rose-500/10 rounded-[--radius-md] transition-colors cursor-pointer"
                        title={`Remove ${device.name}`}
                      >
                        <Trash2 size={15} />
                      </button>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* ── Add Client Modal ── */}
      <AnimatePresence>
        {showRegisterModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="w-full max-w-md bg-[--color-surface-overlay] border border-[--color-border-default] rounded-[--radius-xl] p-6 space-y-4 shadow-2xl"
            >
              <div className="flex items-center justify-between border-b border-[--color-border-subtle] pb-3">
                <div className="flex items-center gap-2">
                  <Smartphone size={18} className="text-[--color-accent]" />
                  <h3 className="text-sm font-bold text-[--color-text-primary]">Connect a New Client Device</h3>
                </div>
                <button
                  onClick={() => {
                    setShowRegisterModal(false)
                    setNewRegistration(null)
                  }}
                  className="text-[--color-text-muted] hover:text-[--color-text-primary]"
                >
                  <X size={16} />
                </button>
              </div>

              {!newRegistration ? (
                <div className="space-y-4">
                  <p className="text-xs text-[--color-text-secondary]">
                    Name the client device (e.g. your iPhone or family member's phone) to generate an authorization token.
                  </p>
                  <div className="space-y-3">
                    <div>
                      <label className="text-[11px] text-[--color-text-muted] uppercase font-semibold block mb-1">
                        Device Name
                      </label>
                      <Input
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        placeholder="e.g. Karthik's iPhone 15"
                        autoFocus
                      />
                    </div>
                    <div>
                      <label className="text-[11px] text-[--color-text-muted] uppercase font-semibold block mb-1">
                        Device Type
                      </label>
                      <select
                        value={newType}
                        onChange={(e) => setNewType(e.target.value as DeviceType)}
                        className="w-full px-3 h-9 text-sm bg-[--color-surface-subtle] text-[--color-text-primary] border border-[--color-border-default] rounded-[--radius-md] focus:outline-none"
                      >
                        <option value="ios">iOS (iPhone / iPad)</option>
                        <option value="android">Android</option>
                        <option value="mac">Mac</option>
                        <option value="web">Web Browser</option>
                      </select>
                    </div>
                  </div>

                  <div className="flex justify-end gap-2 pt-2">
                    <Button size="sm" variant="ghost" onClick={() => setShowRegisterModal(false)}>
                      Cancel
                    </Button>
                    <Button
                      size="sm"
                      variant="accent"
                      onClick={handleRegister}
                      loading={registering}
                      disabled={!newName.trim()}
                    >
                      Generate Client Token
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="p-3 bg-emerald-950/30 border border-emerald-900/50 rounded-[--radius-md] text-xs text-emerald-400">
                    Device registered! Copy this token to authenticate from the new device.
                  </div>

                  <div className="space-y-1">
                    <label className="text-[11px] text-[--color-text-muted] uppercase font-semibold">
                      Client Auth Token (Shown Once)
                    </label>
                    <div className="flex items-center gap-2">
                      <code className="flex-1 text-xs bg-[--color-surface-base] px-3 py-2 rounded-[--radius-md] font-mono break-all select-all border border-[--color-border-subtle]">
                        {newRegistration.token}
                      </code>
                      <Button
                        size="sm"
                        variant="default"
                        onClick={() => {
                          navigator.clipboard.writeText(newRegistration.token)
                          addToast({ type: 'success', message: 'Token copied to clipboard!' })
                        }}
                      >
                        <Copy size={13} />
                      </Button>
                    </div>
                  </div>

                  <div className="pt-3">
                    <Button
                      size="sm"
                      variant="accent"
                      className="w-full"
                      onClick={() => {
                        setShowRegisterModal(false)
                        setNewRegistration(null)
                      }}
                    >
                      Done
                    </Button>
                  </div>
                </div>
              )}
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}

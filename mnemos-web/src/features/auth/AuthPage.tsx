import { useState, useEffect } from 'react'
import { motion } from 'motion/react'
import { Images, Smartphone, Laptop, Globe, ShieldCheck, AlertCircle, RefreshCw } from 'lucide-react'
import { registerDevice, getAuthBootstrap, APIClientError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import type { DeviceType } from '@/types'

function detectDevice(): { name: string; type: DeviceType } {
  if (typeof navigator === 'undefined') return { name: 'My Device', type: 'web' }
  const ua = navigator.userAgent
  if (/iPhone|iPad|iPod/i.test(ua)) return { name: 'My iPhone', type: 'ios' }
  if (/Android/i.test(ua)) return { name: 'My Android', type: 'android' }
  if (/Macintosh/i.test(ua)) return { name: 'My MacBook', type: 'mac' }
  return { name: 'My Browser', type: 'web' }
}

export function AuthPage() {
  const { setSession } = useAuthStore()
  const [checkingBootstrap, setCheckingBootstrap] = useState(true)
  const [networkBlocked, setNetworkBlocked] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [serverUrl, setServerUrl] = useState(() => {
    if (typeof window !== 'undefined') {
      return window.location.origin
    }
    return ''
  })

  // Device setup state for Tailscale clients
  const [detected] = useState(detectDevice)
  const [deviceName, setDeviceName] = useState(detected.name)
  const [deviceType, setDeviceType] = useState<DeviceType>(detected.type)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Manual token override state
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [manualToken, setManualToken] = useState('')

  useEffect(() => {
    let active = true
    async function checkBootstrap() {
      try {
        const res = await getAuthBootstrap()
        if (!active) return

        if (res.is_admin && res.auth_token && res.device_id) {
          // Auto-authenticate host machine as Admin
          setSession({
            deviceId: res.device_id,
            authToken: res.auth_token,
            deviceName: res.device_name || 'Server Host (Admin)',
            serverUrl: window.location.origin,
            isAdmin: true,
          })
          return
        }
        setCheckingBootstrap(false)
      } catch (err) {
        if (!active) return
        if (err instanceof APIClientError && err.status === 403) {
          setNetworkBlocked(true)
        }
        setCheckingBootstrap(false)
      }
    }

    checkBootstrap()
    return () => {
      active = false
    }
  }, [setSession])

  const handleConnectDevice = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!deviceName.trim()) {
      setError('Please enter a name for this device')
      return
    }
    setError(null)
    setIsSubmitting(true)

    try {
      if (manualToken.trim()) {
        setSession({
          deviceId: 'token-device',
          authToken: manualToken.trim(),
          deviceName: deviceName.trim(),
          serverUrl: window.location.origin,
          isAdmin: false,
        })
        return
      }

      const result = await registerDevice(deviceName.trim(), deviceType)
      setSession({
        deviceId: result.device_id,
        authToken: result.auth_token,
        deviceName: deviceName.trim(),
        serverUrl: window.location.origin,
        isAdmin: false,
      })
    } catch (err) {
      if (err instanceof APIClientError) {
        setError(err.message)
      } else {
        setError('Could not connect to PhotoVault. Check your Tailscale connection.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (checkingBootstrap) {
    return (
      <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-4">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="w-12 h-12 rounded-[--radius-lg] bg-[--color-accent] flex items-center justify-center shadow-lg">
            <Images size={24} className="text-black" />
          </div>
          <div className="flex items-center gap-2 text-sm text-[--color-text-secondary]">
            <RefreshCw size={14} className="animate-spin text-[--color-accent]" />
            <span>Connecting to PhotoVault...</span>
          </div>
        </div>
      </div>
    )
  }

  if (networkBlocked) {
    return (
      <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-4">
        <div className="w-full max-w-sm rounded-[--radius-xl] border border-rose-500/20 bg-[--color-surface-overlay] p-6 text-center space-y-4">
          <div className="w-12 h-12 rounded-full bg-rose-500/10 text-rose-500 flex items-center justify-center mx-auto">
            <AlertCircle size={24} />
          </div>
          <h2 className="text-base font-bold text-[--color-text-primary]">
            Network Access Restricted
          </h2>
          <p className="text-xs text-[--color-text-muted] leading-relaxed">
            This PhotoVault server is strictly private and only accepts connections from your personal <strong>Tailscale</strong> network or localhost.
          </p>
          <div className="pt-2">
            <Button size="sm" variant="default" onClick={() => window.location.reload()}>
              Retry Connection
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="w-full max-w-sm"
      >
        {/* Card */}
        <div className="rounded-[--radius-xl] border border-[--color-border-subtle] bg-[--color-surface-overlay] p-6 shadow-xl space-y-6">
          {/* Header */}
          <div className="text-center space-y-2">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-[--radius-lg] bg-[--color-accent] shadow-md mb-1">
              <Images size={22} className="text-black" />
            </div>
            <h1 className="text-lg font-bold tracking-tight text-[--color-text-primary]">
              PhotoVault
            </h1>
            <div className="flex items-center justify-center gap-1.5 text-xs text-emerald-400 font-medium">
              <ShieldCheck size={13} />
              <span>Tailscale Mesh Connected</span>
            </div>
            <p className="text-xs text-[--color-text-muted] leading-relaxed pt-1">
              Identify this device to browse and upload photos to your personal cloud.
            </p>
          </div>

          {/* Form */}
          <form onSubmit={handleConnectDevice} className="space-y-4">
            {/* Device Type Selector */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-[--color-text-secondary]">
                Device Type
              </label>
              <div className="grid grid-cols-4 gap-2">
                {(
                  [
                    { type: 'ios', label: 'iPhone', icon: <Smartphone size={16} /> },
                    { type: 'android', label: 'Android', icon: <Smartphone size={16} /> },
                    { type: 'mac', label: 'Laptop', icon: <Laptop size={16} /> },
                    { type: 'web', label: 'Browser', icon: <Globe size={16} /> },
                  ] as const
                ).map((item) => (
                  <button
                    key={item.type}
                    type="button"
                    onClick={() => {
                      setDeviceType(item.type)
                      if (deviceName.startsWith('My ')) {
                        setDeviceName(
                          item.type === 'ios'
                            ? "My iPhone"
                            : item.type === 'android'
                            ? "My Android"
                            : item.type === 'mac'
                            ? "My MacBook"
                            : "My Browser",
                        )
                      }
                    }}
                    className={`flex flex-col items-center justify-center gap-1 p-2 rounded-[--radius-md] border text-xs cursor-pointer transition-all ${
                      deviceType === item.type
                        ? 'border-[--color-accent] bg-[--color-accent]/10 text-[--color-accent] font-semibold'
                        : 'border-[--color-border-subtle] bg-[--color-surface-subtle] text-[--color-text-secondary] hover:text-[--color-text-primary]'
                    }`}
                  >
                    {item.icon}
                    <span className="text-[10px]">{item.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Device Name Input */}
            <div className="space-y-1">
              <label className="text-xs font-semibold text-[--color-text-secondary]">
                Device Name
              </label>
              <Input
                value={deviceName}
                onChange={(e) => setDeviceName(e.target.value)}
                placeholder="e.g. Karthik's iPhone"
                required
              />
            </div>

            {/* Advanced Options Toggle */}
            <div className="pt-1">
              <button
                type="button"
                onClick={() => setShowAdvanced((s) => !s)}
                className="text-[11px] text-[--color-text-muted] hover:text-[--color-text-primary] underline cursor-pointer"
              >
                {showAdvanced ? 'Hide server & token settings' : 'Advanced settings'}
              </button>
            </div>

            {showAdvanced && (
              <div className="space-y-3 pt-2 border-t border-[--color-border-subtle]">
                <div className="space-y-1">
                  <label className="text-[11px] text-[--color-text-muted]">Server URL</label>
                  <Input
                    value={serverUrl}
                    onChange={(e) => setServerUrl(e.target.value)}
                    placeholder="http://127.0.0.1:8080"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] text-[--color-text-muted]">Token (Optional)</label>
                  <Input
                    value={manualToken}
                    onChange={(e) => setManualToken(e.target.value)}
                    placeholder="Existing bearer token"
                  />
                </div>
              </div>
            )}

            {error && (
              <div className="text-xs text-rose-500 bg-rose-500/10 border border-rose-500/20 p-2.5 rounded-[--radius-md]">
                {error}
              </div>
            )}

            <Button
              type="submit"
              variant="accent"
              size="lg"
              className="w-full font-semibold"
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Connecting...' : 'Connect Device'}
            </Button>
          </form>
        </div>
      </motion.div>
    </div>
  )
}

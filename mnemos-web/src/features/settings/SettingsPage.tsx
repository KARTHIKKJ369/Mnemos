import { useState, useEffect, useRef, useCallback } from 'react'
import {
  Settings,
  LogOut,
  Copy,
  Server,
  FolderSearch,
  FolderUp,
  RefreshCw,
  CheckCircle2,
  Loader2,
  AlertCircle,
  Images,
  Film,
  HardDrive,
  Layers,
  Smartphone,
  Check,
  Save,
} from 'lucide-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getHealth,
  getAuthBootstrap,
  scanStorageFolder,
  pickStorageFolder,
  getScanStatus,
  getStorageConfig,
  updateStorageConfig,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'
import { formatBytes } from '@/lib/utils'

const FOLDER_KEY = 'photovault_selected_folder'
const COLS_KEY = 'photovault_gallery_cols'

function StatCard({
  icon,
  label,
  value,
  subvalue,
}: {
  icon: React.ReactNode
  label: string
  value: string | number
  subvalue?: string
}) {
  return (
    <div className="flex flex-col p-4 bg-[--color-surface-overlay] border border-[--color-border-subtle] rounded-[--radius-lg] shadow-xs">
      <div className="flex items-center justify-between text-[--color-text-muted] mb-2">
        <span className="text-xs font-medium uppercase tracking-wider">{label}</span>
        <div className="p-1.5 rounded-[--radius-md] bg-[--color-surface-subtle] text-[--color-accent]">
          {icon}
        </div>
      </div>
      <span className="text-xl font-bold tracking-tight text-[--color-text-primary] font-mono">
        {value}
      </span>
      {subvalue && (
        <span className="text-[11px] text-[--color-text-muted] mt-1 font-medium">
          {subvalue}
        </span>
      )}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-wider px-1">
        {title}
      </h2>
      <div className="bg-[--color-surface-overlay] border border-[--color-border-subtle] rounded-[--radius-lg] overflow-hidden divide-y divide-[--color-border-subtle]">
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
    <div className="flex items-center gap-4 px-4 py-3">
      <span className="text-xs text-[--color-text-muted] w-36 flex-shrink-0">{label}</span>
      {value && (
        <span className={`text-xs text-[--color-text-primary] flex-1 ${mono ? 'font-mono' : ''} truncate`}>
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
  const [copied, setCopied] = useState(false)

  // Persist the scan path in localStorage so it survives page refreshes
  const [scanPath, setScanPath] = useState<string>(() => localStorage.getItem(FOLDER_KEY) ?? '')
  const [isPicking, setIsPicking] = useState(false)
  const [isScanRequested, setIsScanRequested] = useState(false)
  const [gridCols, setGridCols] = useState<number>(() => {
    const saved = localStorage.getItem(COLS_KEY)
    return saved ? Number(saved) : 5
  })

  const queryClient = useQueryClient()
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (scanPath) {
      localStorage.setItem(FOLDER_KEY, scanPath)
    }
  }, [scanPath])

  const handleSetCols = (num: number) => {
    setGridCols(num)
    localStorage.setItem(COLS_KEY, String(num))
    addToast({ type: 'info', message: `Default grid density set to ${num} columns` })
  }

  const { data: health, isError } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    refetchInterval: 15_000,
  })

  const { data: bootstrapInfo } = useQuery({
    queryKey: ['authBootstrap'],
    queryFn: () => getAuthBootstrap(),
    staleTime: 60_000,
  })
  const isAdmin = bootstrapInfo?.is_admin || session?.isAdmin || false

  const { data: scanStatus, refetch: refetchScanStatus } = useQuery({
    queryKey: ['scanStatus'],
    queryFn: getScanStatus,
    refetchInterval: false,
  })

  // Storage Config (.env)
  const { data: storageConfig, refetch: refetchStorageConfig } = useQuery({
    queryKey: ['storageConfig'],
    queryFn: getStorageConfig,
  })
  const [vaultPathInput, setVaultPathInput] = useState('')
  const [isPickingVault, setIsPickingVault] = useState(false)
  const [isSavingVault, setIsSavingVault] = useState(false)
  const [vaultSavedNotice, setVaultSavedNotice] = useState<string | null>(null)

  useEffect(() => {
    if (storageConfig?.storage_path && !vaultPathInput) {
      setVaultPathInput(storageConfig.storage_path)
    }
  }, [storageConfig?.storage_path, vaultPathInput])

  const handlePickVaultFolder = async () => {
    setIsPickingVault(true)
    try {
      const res = await pickStorageFolder()
      if (res.path && !res.cancelled) {
        setVaultPathInput(res.path)
        setVaultSavedNotice(null)
      }
    } catch {
      addToast({ type: 'error', message: 'Could not open native folder picker' })
    } finally {
      setIsPickingVault(false)
    }
  }

  const handleSaveVaultConfig = async () => {
    if (!vaultPathInput.trim()) {
      addToast({ type: 'error', message: 'Storage path cannot be empty' })
      return
    }
    setIsSavingVault(true)
    try {
      const res = await updateStorageConfig(vaultPathInput.trim())
      setVaultSavedNotice(res.message || 'Saved to .env!')
      await refetchStorageConfig()
      addToast({ type: 'success', message: 'Vault storage path saved to .env' })
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to update .env'
      addToast({ type: 'error', message })
    } finally {
      setIsSavingVault(false)
    }
  }

  // Poll scan status while a scan is running
  const stopPoll = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [])

  const startPoll = useCallback(() => {
    stopPoll()
    pollRef.current = setInterval(async () => {
      const s = await refetchScanStatus()
      if (s.data && !s.data.running) {
        stopPoll()
        setIsScanRequested(false)
        queryClient.invalidateQueries({ queryKey: ['media'] })
        queryClient.invalidateQueries({ queryKey: ['health'] })
        if (s.data.last_result) {
          const r = s.data.last_result
          addToast({
            type: 'success',
            message: `Scan finished: ${r.imported} imported, ${r.already_indexed} already indexed${r.errors > 0 ? `, ${r.errors} errors` : ''}.`,
          })
        }
        if (s.data.last_error) {
          addToast({ type: 'error', message: `Scan error: ${s.data.last_error}` })
        }
      }
    }, 1500)
  }, [refetchScanStatus, stopPoll, queryClient, addToast])

  useEffect(() => {
    if (scanStatus?.running && !pollRef.current) {
      setIsScanRequested(true)
      startPoll()
    }
  }, [scanStatus?.running, startPoll])

  useEffect(() => () => stopPoll(), [stopPoll])

  const handlePickFolder = async () => {
    setIsPicking(true)
    try {
      const res = await pickStorageFolder()
      if (!res.cancelled && res.path) {
        setScanPath(res.path)
        addToast({ type: 'info', message: `Selected: ${res.path}` })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Could not open Finder'
      addToast({ type: 'error', message: msg })
    } finally {
      setIsPicking(false)
    }
  }

  const handleScan = async () => {
    setIsScanRequested(true)
    try {
      await scanStorageFolder(scanPath.trim() || undefined)
      startPoll()
      addToast({ type: 'info', message: 'Scan started — monitoring progress…' })
    } catch (err: unknown) {
      setIsScanRequested(false)
      const msg = err instanceof Error ? err.message : 'Folder scan failed'
      addToast({ type: 'error', message: msg })
    }
  }

  const handleLogout = () => {
    if (!confirming) {
      setConfirming(true)
      return
    }
    clearSession()
    addToast({ type: 'info', message: 'Session disconnected' })
  }

  const uptimeStr = health?.uptime_seconds
    ? `${Math.floor(health.uptime_seconds / 3600)}h ${Math.floor((health.uptime_seconds % 3600) / 60)}m`
    : 'Active'

  const isScanning = isScanRequested || (scanStatus?.running ?? false)

  return (
    <div className="flex flex-col h-full bg-[--color-surface-base]">
      {/* Header */}
      <div className="flex items-center gap-2.5 px-6 py-3.5 border-b border-[--color-border-subtle] bg-[--color-surface-base]">
        <Settings size={16} className="text-[--color-accent]" />
        <h1 className="text-base font-bold text-[--color-text-primary] tracking-tight">System &amp; Storage Settings</h1>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6 max-w-3xl">
        {/* ── Library Overview Stat Cards ── */}
        <div className="space-y-2">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-wider px-1">
            Vault Library Overview
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <StatCard
              icon={<Images size={15} />}
              label="Photos"
              value={health?.total_photos?.toLocaleString() ?? '0'}
              subvalue="Indexed images"
            />
            <StatCard
              icon={<Film size={15} />}
              label="Videos"
              value={health?.total_videos?.toLocaleString() ?? '0'}
              subvalue="Indexed video clips"
            />
            <StatCard
              icon={<Layers size={15} />}
              label="Total Media"
              value={health?.total_media?.toLocaleString() ?? '0'}
              subvalue="Active vault items"
            />
            <StatCard
              icon={<HardDrive size={15} />}
              label="Vault Storage"
              value={health?.vault_bytes ? formatBytes(health.vault_bytes) : '0 B'}
              subvalue="Total media footprint"
            />
            <StatCard
              icon={<Server size={15} />}
              label="Free Disk"
              value={health?.disk_free_bytes ? formatBytes(health.disk_free_bytes) : 'Calculating…'}
              subvalue="Available on disk"
            />
            <StatCard
              icon={<Smartphone size={15} />}
              label="Devices"
              value={health?.total_devices ?? 1}
              subvalue="Paired clients"
            />
          </div>
        </div>

        {/* ── Client Display Preferences ── */}
        <Section title="Display Preferences">
          <div className="flex items-center justify-between px-4 py-3">
            <div>
              <p className="text-xs font-medium text-[--color-text-primary]">Gallery Grid Density</p>
              <p className="text-[11px] text-[--color-text-muted]">Choose default number of columns in the photo gallery</p>
            </div>
            <div className="flex items-center gap-1 bg-[--color-surface-subtle] p-0.5 rounded-[--radius-md] border border-[--color-border-subtle]">
              {[4, 5, 6].map((num) => (
                <button
                  key={num}
                  onClick={() => handleSetCols(num)}
                  className={`px-3 py-1 rounded text-xs font-medium cursor-pointer transition-colors ${
                    gridCols === num
                      ? 'bg-[--color-surface-overlay] text-[--color-text-primary] font-semibold shadow-xs'
                      : 'text-[--color-text-muted] hover:text-[--color-text-primary]'
                  }`}
                >
                  {num} cols
                </button>
              ))}
            </div>
          </div>
        </Section>

        {/* ── Server Host Configuration (Admin Only) ── */}
        {isAdmin ? (
          <>
            <Section title="Server Host Configuration">
              <Row
                label="Server Status"
                value={isError ? 'Unreachable' : 'Online & Healthy'}
                action={
                  <span className={`w-2 h-2 rounded-full ${isError ? 'bg-red-500' : 'bg-emerald-500 animate-pulse'}`} />
                }
              />
              <Row label="Server Address" value={session?.serverUrl || window.location.origin} mono />
              <Row
                label="Storage Directory"
                value={health?.storage_path || 'storage'}
                mono
              />
              <Row
                label="Free Storage Space"
                value={health?.disk_free_bytes ? formatBytes(health.disk_free_bytes) : 'Calculating…'}
                mono
              />
              <Row label="Server Uptime" value={uptimeStr} />
              <Row
                label="Database Engine"
                value={health?.database === 'ok' ? 'SQLite (Ready · Fast WAL)' : 'Error'}
              />
              <Row label="PhotoVault Version" value={`v${health?.version || '1.0.0'}`} mono />
            </Section>

            {/* ── Vault Storage Location (.env) ── */}
            <Section title="Vault Storage Location (.env Configuration)">
              <div className="p-4 space-y-3">
                <p className="text-xs text-[--color-text-muted] leading-relaxed">
                  PhotoVault stores its SQLite database (<code className="text-[11px] px-1 py-0.5 rounded bg-[--color-surface-subtle] text-[--color-text-primary]">vault.db</code>), deduplicated blobs, and derived thumbnails inside this directory. You can select a folder on disk or an external hard drive here and save it to <code className="text-[11px] px-1 py-0.5 rounded bg-[--color-surface-subtle] text-[--color-text-primary]">.env</code> so you don't need to specify <code className="text-[11px] px-1 py-0.5 rounded bg-[--color-surface-subtle] text-[--color-text-primary]">-storage</code> on the command line.
                </p>

                {storageConfig && (
                  <div className="text-[11px] text-[--color-text-muted] flex flex-wrap gap-x-4 gap-y-1">
                    <span>Active Vault Storage: <strong className="font-mono text-[--color-text-primary]">{storageConfig.storage_path}</strong></span>
                    <span>Config File: <strong className="font-mono text-[--color-text-secondary]">{storageConfig.env_path}</strong></span>
                  </div>
                )}

                <div className="flex flex-col sm:flex-row gap-2">
                  <input
                    type="text"
                    value={vaultPathInput}
                    onChange={(e) => {
                      setVaultPathInput(e.target.value)
                      setVaultSavedNotice(null)
                    }}
                    placeholder={storageConfig?.storage_path || '/path/to/vault/storage'}
                    className="flex-1 bg-[--color-surface-subtle] text-xs font-mono text-[--color-text-primary] px-3 py-2 rounded-[--radius-md] border border-[--color-border-subtle] focus:outline-none focus:border-[--color-accent]"
                    disabled={isSavingVault}
                  />
                  <Button
                    size="sm"
                    variant="default"
                    onClick={handlePickVaultFolder}
                    disabled={isPickingVault || isSavingVault}
                    className="gap-1.5 flex-shrink-0"
                    title="Select folder using macOS Finder"
                  >
                    <FolderUp size={13} className="text-[--color-accent]" />
                    <span>{isPickingVault ? 'Opening Finder...' : 'Select in Finder'}</span>
                  </Button>
                  <Button
                    size="sm"
                    variant="accent"
                    onClick={handleSaveVaultConfig}
                    disabled={isSavingVault || isPickingVault || !vaultPathInput.trim()}
                    className="gap-1.5 flex-shrink-0"
                  >
                    {isSavingVault ? (
                      <>
                        <Loader2 size={13} className="animate-spin" />
                        <span>Saving…</span>
                      </>
                    ) : (
                      <>
                        <Save size={13} />
                        <span>Save to .env</span>
                      </>
                    )}
                  </Button>
                </div>

                {vaultSavedNotice && (
                  <div className="p-3 bg-amber-950/30 border border-amber-800/40 rounded-[--radius-md] text-xs text-amber-300 space-y-1">
                    <div className="flex items-center gap-1.5 font-semibold text-amber-200">
                      <CheckCircle2 size={13} className="text-emerald-400" />
                      <span>Saved to .env</span>
                    </div>
                    <p className="text-[11px] text-amber-300/90 leading-relaxed">
                      {vaultSavedNotice} Next time you run <code className="px-1 py-0.5 rounded bg-black/40 text-amber-100 font-mono">go run ./cmd/photovault</code>, it will automatically load this directory without requiring any command-line flags.
                    </p>
                  </div>
                )}
              </div>
            </Section>

            {/* ── Folder Ingestion & Library Scanner ── */}
            <Section title="Folder Ingestion &amp; Library Scanner">
              <div className="p-4 space-y-3">
                <p className="text-xs text-[--color-text-muted] leading-relaxed">
                  Scan any folder on your server to discover and ingest existing photos and videos.
                  Files are hashed, deduplicated, EXIF dates are indexed, and queued for thumbnail generation.
                  The scan runs in the background — you can navigate away without interrupting it.
                </p>

                {/* Selected folder display */}
                {scanPath && (
                  <div className="flex items-center gap-2 px-3 py-2 rounded-[--radius-md] bg-[--color-surface-subtle] border border-[--color-border-subtle]">
                    <FolderSearch size={13} className="text-[--color-accent] flex-shrink-0" />
                    <span className="text-xs font-mono text-[--color-text-primary] flex-1 truncate">{scanPath}</span>
                    {!isScanning && (
                      <button
                        onClick={() => {
                          setScanPath('')
                          localStorage.removeItem(FOLDER_KEY)
                        }}
                        className="text-[--color-text-muted] hover:text-[--color-text-primary] ml-1 text-[11px] flex-shrink-0 cursor-pointer"
                        title="Clear selected folder"
                      >
                        ✕
                      </button>
                    )}
                  </div>
                )}

                <div className="flex flex-col sm:flex-row gap-2">
                  <input
                    type="text"
                    value={scanPath}
                    onChange={(e) => setScanPath(e.target.value)}
                    placeholder={health?.storage_path ? `${health.storage_path} (default storage)` : 'Select or enter folder path'}
                    className="flex-1 bg-[--color-surface-subtle] text-xs text-[--color-text-primary] px-3 py-2 rounded-[--radius-md] border border-[--color-border-subtle] focus:outline-none focus:border-[--color-accent]"
                    disabled={isScanning}
                  />
                  <Button
                    size="sm"
                    variant="default"
                    onClick={handlePickFolder}
                    disabled={isPicking || isScanning}
                    className="gap-1.5 flex-shrink-0"
                    title="Open native macOS Finder to select a folder"
                  >
                    <FolderUp size={13} className="text-[--color-accent]" />
                    <span>{isPicking ? 'Opening Finder...' : 'Select in Finder'}</span>
                  </Button>
                  <Button
                    size="sm"
                    variant="accent"
                    onClick={handleScan}
                    disabled={isScanning || isPicking}
                    className="gap-1.5 flex-shrink-0"
                  >
                    {isScanning ? (
                      <>
                        <Loader2 size={13} className="animate-spin" />
                        <span>Scanning…</span>
                      </>
                    ) : (
                      <>
                        <RefreshCw size={13} />
                        <span>Scan Folder</span>
                      </>
                    )}
                  </Button>
                </div>

                {/* Live status while scanning */}
                {isScanning && (
                  <div className="flex items-center gap-2 px-3 py-2 bg-[--color-surface-subtle] border border-[--color-border-subtle] rounded-[--radius-md] text-xs text-[--color-text-secondary]">
                    <Loader2 size={12} className="animate-spin text-[--color-accent]" />
                    <span>Scanning in progress — safe to navigate away, scan continues in background.</span>
                  </div>
                )}

                {/* Last result */}
                {!isScanning && scanStatus?.last_result && (
                  <div className="p-3 bg-[--color-surface-subtle] rounded-[--radius-md] border border-[--color-border-subtle] text-xs space-y-1">
                    <div className="flex items-center gap-1.5 font-semibold text-emerald-400">
                      <CheckCircle2 size={12} />
                      Scan Complete
                    </div>
                    <div className="text-[--color-text-secondary] flex flex-wrap gap-x-4 gap-y-1 text-[11px] pt-1">
                      <span>Scanned: <strong>{scanStatus.last_result.scanned}</strong></span>
                      <span>Imported: <strong className="text-[--color-accent]">{scanStatus.last_result.imported}</strong></span>
                      <span>Already in Library: <strong>{scanStatus.last_result.already_indexed}</strong></span>
                      {scanStatus.last_result.errors > 0 && (
                        <span className="text-rose-400">Errors: {scanStatus.last_result.errors}</span>
                      )}
                    </div>
                  </div>
                )}

                {/* Error state */}
                {!isScanning && scanStatus?.last_error && (
                  <div className="flex items-center gap-2 px-3 py-2 bg-rose-950/40 border border-rose-800/40 rounded-[--radius-md] text-xs text-rose-400">
                    <AlertCircle size={12} />
                    {scanStatus.last_error}
                  </div>
                )}
              </div>
            </Section>
          </>
        ) : (
          /* ── Client Device Server Status ── */
          <Section title="Connected PhotoVault Server">
            <Row
              label="Server Status"
              value={isError ? 'Unreachable' : 'Online & Connected'}
              action={
                <span className={`w-2 h-2 rounded-full ${isError ? 'bg-red-500' : 'bg-emerald-500 animate-pulse'}`} />
              }
            />
            <Row label="Server Address" value={session?.serverUrl || window.location.origin} mono />
            <Row label="Server Version" value={`v${health?.version || '1.0.0'}`} mono />
            <Row label="Device Access" value="Paired Client Device" />
          </Section>
        )}

        {/* ── Current Client Device Identity ── */}
        <Section title="This Client Device">
          <Row label="Device Name" value={session?.deviceName || 'Active Client'} />
          <Row label="Client Device ID" value={session?.deviceId || 'Local Browser'} mono />
          <Row
            label="Auth Token"
            value={session?.authToken ? `${session.authToken.slice(0, 10)}••••••••` : 'Active Session'}
            mono
            action={
              session?.authToken ? (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    navigator.clipboard.writeText(session.authToken)
                    setCopied(true)
                    setTimeout(() => setCopied(false), 2000)
                    addToast({ type: 'success', message: 'Auth token copied to clipboard' })
                  }}
                  className="gap-1 text-xs"
                >
                  {copied ? <Check size={12} className="text-emerald-400" /> : <Copy size={12} />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </Button>
              ) : undefined
            }
          />
        </Section>

        {/* How to Connect Other Devices */}
        <div className="p-4 bg-[--color-surface-overlay] border border-[--color-border-subtle] rounded-[--radius-lg] space-y-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-[--color-text-primary]">
            <Server size={14} className="text-[--color-accent]" />
            <span>Connecting other client devices (Phones, Laptops)</span>
          </div>
          <p className="text-xs text-[--color-text-muted] leading-relaxed">
            To connect your phone or another laptop to this PhotoVault server, open the app on that device,
            point the server URL to this machine's IP (or Tailscale address), and log in with a client token
            created on the <strong>Clients &amp; Devices</strong> page.
          </p>
        </div>

        {/* Danger zone / disconnect */}
        <div className="pt-2">
          <Button
            variant="destructive"
            size="sm"
            onClick={handleLogout}
            className="gap-2 text-xs"
          >
            <LogOut size={13} />
            {confirming ? 'Confirm Disconnect?' : 'Disconnect This Device'}
          </Button>
        </div>
      </div>
    </div>
  )
}


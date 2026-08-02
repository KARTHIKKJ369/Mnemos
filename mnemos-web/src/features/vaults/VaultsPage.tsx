import { useState } from 'react'
import { motion } from 'motion/react'
import { Lock, ShieldCheck, Plus, Key } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { createVault } from '@/api/client'
import { Button } from '@/components/ui/Button'
import { useUIStore } from '@/stores/ui'
import type { VaultCreateResponse } from '@/types'

function VaultCard({ vault }: { vault: VaultCreateResponse }) {
  // note: uses useUIStore.getState() for imperative access
  const isEncrypted = vault.type === 'encrypted'

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
      className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-3 border border-[--color-border-default]"
    >
      <div className="flex items-center gap-3">
        <div className={`w-8 h-8 rounded-[--radius-md] flex items-center justify-center ${
          isEncrypted ? 'bg-amber-950 text-amber-400' : 'bg-[--color-surface-subtle] text-[--color-text-muted]'
        }`}>
          {isEncrypted ? <ShieldCheck size={15} /> : <Lock size={15} />}
        </div>
        <div>
          <p className="text-sm font-medium text-[--color-text-primary]">
            {isEncrypted ? 'Encrypted vault' : 'Legacy vault'}
          </p>
          <p className="text-xs text-[--color-text-muted] font-mono">{vault.vault_id.slice(0, 16)}…</p>
        </div>
        <span className={`ml-auto text-xs px-2 py-0.5 rounded-full ${
          isEncrypted
            ? 'bg-amber-950 text-amber-400'
            : 'bg-[--color-surface-subtle] text-[--color-text-muted]'
        }`}>
          {vault.type}
        </span>
      </div>

      {isEncrypted && vault.salt && (
        <div className="space-y-1.5 pt-1">
          <p className="text-xs text-amber-400 font-semibold">⚠ Save these parameters — required for decryption</p>
          <div className="bg-[--color-surface-base] rounded-[--radius-md] p-3 space-y-1.5 font-mono">
            <div className="flex gap-2">
              <span className="text-[11px] text-[--color-text-muted] w-20">Salt</span>
              <code className="text-[11px] text-[--color-text-secondary] break-all flex-1 select-all">{vault.salt}</code>
            </div>
            {vault.argon2 && (
              <>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Argon2 time</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.time}</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Memory</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.memory_kib} KiB</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Threads</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.argon2.threads}</code>
                </div>
                <div className="flex gap-2">
                  <span className="text-[11px] text-[--color-text-muted] w-20">Algorithm v</span>
                  <code className="text-[11px] text-[--color-text-secondary]">{vault.algorithm_version}</code>
                </div>
              </>
            )}
          </div>
          <Button
            size="sm"
            variant="ghost"
            className="w-full"
            onClick={() => {
              navigator.clipboard.writeText(JSON.stringify(vault, null, 2))
              useUIStore.getState().addToast({ type: 'success', message: 'Vault parameters copied!' })
            }}
          >
            <Key size={12} />
            Copy all parameters
          </Button>
        </div>
      )}
    </motion.div>
  )
}

export function VaultsPage() {
  const { addToast } = useUIStore()
  const [vaults, setVaults] = useState<VaultCreateResponse[]>([])

  const createMutation = useMutation({
    mutationFn: createVault,
    onSuccess: (vault) => {
      setVaults((v) => [vault, ...v])
      addToast({
        type: 'success',
        message: vault.type === 'encrypted'
          ? 'Encrypted vault created — save your parameters!'
          : 'Legacy vault created',
      })
    },
    onError: () => addToast({ type: 'error', message: 'Failed to create vault' }),
  })

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[--color-border-subtle]">
        <div className="flex items-center gap-2">
          <Lock size={15} className="text-[--color-text-muted]" />
          <h1 className="text-sm font-semibold text-[--color-text-primary]">Vaults</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => createMutation.mutate('legacy')}
            loading={createMutation.isPending && createMutation.variables === 'legacy'}
          >
            <Plus size={12} />
            Legacy
          </Button>
          <Button
            size="sm"
            variant="accent"
            onClick={() => createMutation.mutate('encrypted')}
            loading={createMutation.isPending && createMutation.variables === 'encrypted'}
          >
            <ShieldCheck size={12} />
            Encrypted
          </Button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {/* Explainer */}
        <div className="bg-[--color-surface-overlay] rounded-[--radius-lg] p-4 space-y-2">
          <h2 className="text-xs font-semibold text-[--color-text-muted] uppercase tracking-widest">
            About vaults
          </h2>
          <div className="space-y-1.5 text-xs text-[--color-text-muted]">
            <p>
              <span className="text-[--color-text-secondary] font-medium">Legacy vaults</span> — access-controlled hidden storage, excluded from normal gallery and sync.
            </p>
            <p>
              <span className="text-[--color-text-secondary] font-medium">Encrypted vaults</span> — client-owned Argon2id key derivation + AES-256-GCM. The server never sees your passphrase or plaintext content.
            </p>
            <p className="text-[--color-text-disabled]">
              Note: vault listing and file management within vaults are pending backend implementation.
            </p>
          </div>
        </div>

        {/* Vault list */}
        {vaults.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
            <div className="w-12 h-12 rounded-[--radius-xl] bg-[--color-surface-overlay] flex items-center justify-center">
              <Lock size={20} className="text-[--color-text-disabled]" />
            </div>
            <p className="text-sm text-[--color-text-secondary]">No vaults created yet</p>
            <p className="text-xs text-[--color-text-muted] max-w-48">
              Create a legacy or encrypted vault using the buttons above.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {vaults.map((v) => (
              <VaultCard key={v.vault_id} vault={v} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}


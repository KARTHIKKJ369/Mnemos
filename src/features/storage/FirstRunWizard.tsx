import { useState } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { HardDrive, FolderOpen, ArrowRight, Check } from 'lucide-react'
import { useCreateStorage, useSelectFolder } from '@/hooks/useStorage'
import { useUIStore } from '@/stores/ui'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { cn } from '@/lib/utils'

type Step = 'name' | 'folder' | 'confirm'

interface WizardState { name: string; path: string }

function StepDot({ active, done }: { active: boolean; done: boolean }) {
  return (
    <div className={cn(
      'w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold transition-colors duration-[200ms]',
      done ? 'bg-[--color-success] text-white'
           : active ? 'bg-[--color-accent] text-[--color-surface-base]'
           : 'bg-[--color-surface-subtle] text-[--color-text-muted]',
    )}>
      {done ? <Check size={10} /> : null}
    </div>
  )
}

export function FirstRunWizard() {
  const { addToast } = useUIStore()
  const [step, setStep] = useState<Step>('name')
  const [state, setState] = useState<WizardState>({ name: 'Main Library', path: '' })
  const [direction, setDirection] = useState(1)
  const [manualPath, setManualPath] = useState('')

  const selectFolder = useSelectFolder()
  const createStorage = useCreateStorage()

  const goTo = (next: Step, dir = 1) => { setDirection(dir); setStep(next) }

  const handleSelectFolder = async () => {
    try {
      const result = await selectFolder.mutateAsync()
      setState((s) => ({ ...s, path: result.path }))
      goTo('confirm')
    } catch {
      // Backend folder picker not available — user falls back to manual input
      addToast({ type: 'info', message: 'Folder picker unavailable — enter path manually below.' })
    }
  }

  const handleManualContinue = () => {
    const p = manualPath.trim()
    if (!p) return
    setState((s) => ({ ...s, path: p }))
    goTo('confirm')
  }

  const handleCreate = async () => {
    if (!state.name.trim() || !state.path) return
    try {
      await createStorage.mutateAsync({ name: state.name.trim(), path: state.path })
      addToast({ type: 'success', message: `"${state.name}" library created` })
    } catch {
      addToast({ type: 'error', message: 'Failed to create library — check server logs.' })
    }
  }

  const stepIndex: Record<Step, number> = { name: 0, folder: 1, confirm: 2 }
  const currentIndex = stepIndex[step]

  const variants = {
    enter: (d: number) => ({ opacity: 0, x: d > 0 ? 24 : -24 }),
    center: { opacity: 1, x: 0 },
    exit:  (d: number) => ({ opacity: 0, x: d > 0 ? -24 : 24 }),
  }

  return (
    <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.3 }}
        className="w-full max-w-md"
      >
        {/* Header */}
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-[--radius-lg] bg-[--color-accent] flex items-center justify-center">
            <HardDrive size={17} className="text-[--color-surface-base]" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-[--color-text-primary] tracking-tight">Welcome to Mnemos</h1>
            <p className="text-xs text-[--color-text-muted]">No storage library has been configured.</p>
          </div>
        </div>

        {/* Steps */}
        <div className="flex items-center gap-2 mb-8">
          {(['name', 'folder', 'confirm'] as Step[]).map((s, i) => (
            <div key={s} className="flex items-center gap-2">
              <StepDot active={step === s} done={currentIndex > i} />
              {i < 2 && (
                <div className={cn(
                  'h-px w-8 transition-colors duration-[300ms]',
                  currentIndex > i ? 'bg-[--color-success]' : 'bg-[--color-border-default]',
                )} />
              )}
            </div>
          ))}
          <div className="flex-1" />
          <span className="text-xs text-[--color-text-muted]">Step {currentIndex + 1} of 3</span>
        </div>

        {/* Content */}
        <div className="relative min-h-64">
          <AnimatePresence mode="wait" custom={direction}>
            {step === 'name' && (
              <motion.div key="name" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-6"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Name your library</h2>
                  <p className="text-sm text-[--color-text-secondary]">Give this storage library a name. You can rename it later.</p>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-[--color-text-secondary]">Library name</label>
                  <Input value={state.name} onChange={(e) => setState((s) => ({ ...s, name: e.target.value }))}
                    placeholder="Main Library" autoFocus />
                </div>
                <Button variant="accent" size="lg" className="w-full"
                  onClick={() => goTo('folder')} disabled={!state.name.trim()}>
                  Continue <ArrowRight size={14} />
                </Button>
              </motion.div>
            )}

            {step === 'folder' && (
              <motion.div key="folder" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-4"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Choose a folder</h2>
                  <p className="text-sm text-[--color-text-secondary]">
                    Select where photos will be stored on the server.
                  </p>
                </div>

                {/* Option A: server-side picker */}
                <Button variant="outline" size="lg" className="w-full"
                  onClick={handleSelectFolder} loading={selectFolder.isPending}>
                  <FolderOpen size={14} />
                  Open folder picker on server
                </Button>

                {/* Divider */}
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-px bg-[--color-border-default]" />
                  <span className="text-xs text-[--color-text-disabled]">or enter path manually</span>
                  <div className="flex-1 h-px bg-[--color-border-default]" />
                </div>

                {/* Option B: manual path */}
                <div className="space-y-2">
                  <Input
                    value={manualPath}
                    onChange={(e) => setManualPath(e.target.value)}
                    placeholder="/Volumes/Photos or /Users/you/Pictures"
                    className="font-mono text-xs"
                  />
                  <Button variant="accent" size="lg" className="w-full"
                    onClick={handleManualContinue} disabled={!manualPath.trim()}>
                    Continue <ArrowRight size={14} />
                  </Button>
                </div>

                <Button variant="ghost" size="sm" className="w-full" onClick={() => goTo('name', -1)}>
                  Back
                </Button>
              </motion.div>
            )}

            {step === 'confirm' && (
              <motion.div key="confirm" custom={direction} variants={variants}
                initial="enter" animate="center" exit="exit"
                transition={{ type: 'spring', bounce: 0, duration: 0.22 }}
                className="absolute inset-0 space-y-6"
              >
                <div>
                  <h2 className="text-lg font-semibold text-[--color-text-primary] mb-1">Confirm library</h2>
                  <p className="text-sm text-[--color-text-secondary]">Review before creating.</p>
                </div>
                <div className="bg-[--color-surface-overlay] rounded-[--radius-xl] border border-[--color-border-default] divide-y divide-[--color-border-subtle]">
                  <div className="flex items-start gap-3 px-4 py-3">
                    <span className="text-xs text-[--color-text-muted] w-16 flex-shrink-0 pt-0.5">Name</span>
                    <span className="text-sm text-[--color-text-primary] font-medium">{state.name}</span>
                  </div>
                  <div className="flex items-start gap-3 px-4 py-3">
                    <span className="text-xs text-[--color-text-muted] w-16 flex-shrink-0 pt-0.5">Folder</span>
                    <span className="text-xs text-[--color-text-secondary] font-mono break-all">{state.path}</span>
                  </div>
                </div>
                <Button variant="accent" size="lg" className="w-full"
                  onClick={handleCreate} loading={createStorage.isPending}>
                  Create Library
                </Button>
                <Button variant="ghost" size="sm" className="w-full" onClick={() => goTo('folder', -1)}>
                  Back
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  )
}

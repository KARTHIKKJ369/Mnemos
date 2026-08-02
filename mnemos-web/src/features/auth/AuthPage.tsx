import { useState } from 'react'
import { motion } from 'motion/react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Images, Server, Smartphone } from 'lucide-react'
import { registerDevice, APIClientError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

const schema = z.object({
  serverUrl: z.string().url('Must be a valid URL'),
  deviceName: z.string().min(1, 'Required').max(100, 'Too long'),
  deviceType: z.enum(['ios', 'android', 'mac', 'web'] as const),
})

type FormData = z.infer<typeof schema>

export function AuthPage() {
  const { setSession } = useAuthStore()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      serverUrl: 'http://127.0.0.1:8080',
      deviceName: 'My Browser',
      deviceType: 'web',
    },
  })

  const onSubmit = async (data: FormData) => {
    setError(null)
    try {
      const result = await registerDevice(data.deviceName, data.deviceType)
      setSession({
        deviceId: result.device_id,
        authToken: result.auth_token,
        deviceName: data.deviceName,
        serverUrl: data.serverUrl,
      })
    } catch (err) {
      if (err instanceof APIClientError) {
        setError(err.message)
      } else {
        setError('Could not connect to the server. Check the URL and try again.')
      }
    }
  }

  return (
    <div className="min-h-screen bg-[--color-surface-base] flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'spring', bounce: 0, duration: 0.32 }}
        className="w-full max-w-sm"
      >
        <div className="flex items-center gap-3 mb-10">
          <div className="w-9 h-9 rounded-[--radius-lg] bg-[--color-accent] flex items-center justify-center">
            <Images size={18} className="text-[--color-surface-base]" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-[--color-text-primary] tracking-tight">Mnemos</h1>
            <p className="text-xs text-[--color-text-muted]">Self-hosted photo vault</p>
          </div>
        </div>

        <div className="space-y-1 mb-8">
          <h2 className="text-xl font-semibold text-[--color-text-primary] tracking-tight">Connect to server</h2>
          <p className="text-sm text-[--color-text-secondary]">Register this browser as a new device.</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Server URL</label>
            <Input {...register('serverUrl')} placeholder="http://100.x.x.x:8080" leftIcon={<Server size={13} />} autoComplete="url" />
            {errors.serverUrl && <p className="text-xs text-[--color-danger]">{errors.serverUrl.message}</p>}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Device name</label>
            <Input {...register('deviceName')} placeholder="My Browser" leftIcon={<Smartphone size={13} />} autoComplete="off" />
            {errors.deviceName && <p className="text-xs text-[--color-danger]">{errors.deviceName.message}</p>}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[--color-text-secondary]">Device type</label>
            <select
              {...register('deviceType')}
              className="w-full px-3 h-9 text-sm bg-[--color-surface-overlay] text-[--color-text-primary] border border-[--color-border-default] rounded-[--radius-md] focus:outline-none focus:border-[--color-accent] transition-colors duration-[150ms]"
            >
              <option value="web">Web</option>
              <option value="mac">Mac</option>
              <option value="ios">iOS</option>
              <option value="android">Android</option>
            </select>
          </div>

          {error && (
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-xs text-[--color-danger] bg-[--color-danger-surface] px-3 py-2 rounded-[--radius-md]"
            >
              {error}
            </motion.p>
          )}

          <Button type="submit" variant="accent" size="lg" loading={isSubmitting} className="w-full mt-2">
            Register device
          </Button>
        </form>

        <p className="text-xs text-[--color-text-disabled] mt-8 text-center">
          The auth token is shown only once and stored locally.
        </p>
      </motion.div>
    </div>
  )
}


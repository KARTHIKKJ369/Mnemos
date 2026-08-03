import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type Variant = 'default' | 'ghost' | 'destructive' | 'outline' | 'accent'
type Size = 'sm' | 'md' | 'lg' | 'icon'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

const variants: Record<Variant, string> = {
  default:
    'bg-[--color-surface-subtle] text-[--color-text-primary] hover:bg-[--color-surface-muted] ' +
    'border border-[--color-border-default]',
  accent:
    'bg-[--color-accent] text-[--color-surface-base] hover:bg-[--color-accent-dim] ' +
    'border border-transparent font-medium',
  ghost:
    'bg-transparent text-[--color-text-secondary] hover:bg-[--color-surface-overlay] ' +
    'hover:text-[--color-text-primary] border border-transparent',
  outline:
    'bg-transparent text-[--color-text-primary] border border-[--color-border-default] ' +
    'hover:bg-[--color-surface-overlay]',
  destructive:
    'bg-[--color-danger-surface] text-[--color-danger] hover:bg-red-900 ' +
    'border border-red-900',
}

const sizes: Record<Size, string> = {
  sm: 'h-7 px-3 text-xs gap-1.5 rounded-[--radius-md]',
  md: 'h-9 px-4 text-sm gap-2 rounded-[--radius-md]',
  lg: 'h-11 px-6 text-sm gap-2 rounded-[--radius-lg]',
  icon: 'h-9 w-9 rounded-[--radius-md] justify-center',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'default', size = 'md', loading, disabled, children, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled ?? loading}
        className={cn(
          // Base
          'inline-flex items-center cursor-pointer select-none',
          'font-medium transition-none',
          'focus-visible:outline-2 focus-visible:outline-[--color-accent] focus-visible:outline-offset-2',
          'disabled:opacity-40 disabled:cursor-not-allowed',
          // Apple-physics: instant response on pointer-down
          'active:scale-[0.97] active:transition-transform active:duration-[80ms]',
          // Release spring: ease-out slightly longer
          'transition-transform duration-[150ms] ease-out',
          variants[variant],
          sizes[size],
          className,
        )}
        {...props}
      >
        {loading ? (
          <span className="animate-spin h-3.5 w-3.5 border-2 border-current border-t-transparent rounded-full" />
        ) : (
          children
        )}
      </button>
    )
  },
)

Button.displayName = 'Button'


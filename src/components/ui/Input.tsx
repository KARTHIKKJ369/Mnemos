import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  leftIcon?: React.ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, leftIcon, ...props }, ref) => {
    if (leftIcon) {
      return (
        <div className="relative flex items-center">
          <span className="absolute left-3 text-[--color-text-muted] pointer-events-none">
            {leftIcon}
          </span>
          <input
            ref={ref}
            className={cn(
              'w-full pl-9 pr-3 h-9 text-sm',
              'bg-[--color-surface-overlay] text-[--color-text-primary]',
              'border border-[--color-border-default] rounded-[--radius-md]',
              'placeholder:text-[--color-text-disabled]',
              'focus:outline-none focus:border-[--color-accent]',
              'transition-colors duration-[150ms]',
              className,
            )}
            {...props}
          />
        </div>
      )
    }

    return (
      <input
        ref={ref}
        className={cn(
          'w-full px-3 h-9 text-sm',
          'bg-[--color-surface-overlay] text-[--color-text-primary]',
          'border border-[--color-border-default] rounded-[--radius-md]',
          'placeholder:text-[--color-text-disabled]',
          'focus:outline-none focus:border-[--color-accent]',
          'transition-colors duration-[150ms]',
          className,
        )}
        {...props}
      />
    )
  },
)

Input.displayName = 'Input'


import type { ComponentPropsWithRef } from 'react'
import { Spinner } from './Spinner'
import { buttonClasses, type ButtonSize, type ButtonVariant } from './ui'

interface ButtonProps extends ComponentPropsWithRef<'button'> {
  variant?: ButtonVariant
  size?: ButtonSize
  /**
   * Disables the button, marks it busy, and shows the spinner. The label still
   * has to change to the -ing form ("Depositing…") at the call site: a spinner
   * says work is happening, only the words say which work, and screen readers
   * never see the spinner at all.
   */
  loading?: boolean
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  type = 'button',
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClasses(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner /> : null}
      {children}
    </button>
  )
}

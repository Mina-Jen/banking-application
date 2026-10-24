import { formatPesos } from '../lib/money'
import { Input } from './Input'
import { money } from './ui'
import type { Ref } from 'react'

interface MoneyInputProps {
  label: string
  value: string
  onChange(value: string): void
  /**
   * The message to show, or null. It comes from the form's Zod schema, so the
   * field no longer decides for itself what is valid — one rule, one place.
   */
  error?: string | null
  hint?: string
  disabled?: boolean
  name?: string
  autoFocus?: boolean
  onBlur?: () => void
  /** React Hook Form hands out a ref for focusing the first invalid field. */
  inputRef?: Ref<HTMLInputElement>
}

/**
 * Money input.
 *
 * `type="text"` with `inputMode="decimal"` is deliberate: `type="number"` hands
 * the value back as a JS number in some browsers and silently allows exponent
 * notation ("1e5"), both of which break the "money is never a float" rule. Here
 * the raw string the user typed is what reaches the schema and then the backend.
 */
export function MoneyInput({
  label,
  value,
  onChange,
  error = null,
  hint,
  disabled = false,
  name,
  autoFocus = false,
  onBlur,
  inputRef,
}: MoneyInputProps) {
  const shownError = error !== null && error !== '' ? error : null
  // A well-formed amount is echoed back with separators, so "1234.5" is
  // confirmed as ₱1,234.5 before submit.
  const preview = shownError === null && value.trim() !== '' ? formatPesos(value.trim()) : null

  return (
    <Input
      label={label}
      prefix="₱"
      inputClassName={money}
      name={name}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      autoFocus={autoFocus}
      placeholder="0.00"
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
      onBlur={onBlur}
      inputRef={inputRef}
      invalid={shownError !== null}
      hint={shownError ?? preview ?? hint ?? 'Greater than zero, at most two decimals.'}
    />
  )
}

import { useId, type Ref } from 'react'
import { formatPesos } from '../lib/money'
import { cx, hint as hintClass, input as inputClass, inputError, label as labelClass } from './ui'

interface AmountFieldProps {
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
export function AmountField({
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
}: AmountFieldProps) {
  const id = useId()
  const shownError = error !== null && error !== '' ? error : null
  // A well-formed amount is echoed back with separators, so "1234.5" is
  // confirmed as ₱1,234.5 before submit.
  const preview = shownError === null && value.trim() !== '' ? formatPesos(value.trim()) : null

  return (
    <div>
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <div className="relative mt-1">
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500"
        >
          ₱
        </span>
        <input
          ref={inputRef}
          id={id}
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
          aria-invalid={shownError !== null || undefined}
          aria-describedby={`${id}-hint`}
          className={cx(inputClass, 'pl-7 tabular-money', shownError && inputError)}
        />
      </div>
      <p
        id={`${id}-hint`}
        // Errors appear after a failed submit, so they announce themselves;
        // neutral guidance stays quiet.
        role={shownError !== null ? 'alert' : undefined}
        className={cx(hintClass, shownError && 'font-medium text-red-700')}
      >
        {shownError ?? preview ?? hint ?? 'Greater than zero, at most two decimals.'}
      </p>
    </div>
  )
}

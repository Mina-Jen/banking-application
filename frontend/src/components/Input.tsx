import { useId, type InputHTMLAttributes, type ReactNode, type Ref } from 'react'
import {
  cx,
  hint as hintClass,
  hintError,
  hintErrorOnDark,
  hintIdle,
  hintIdleOnDark,
  input as inputClass,
  inputError,
  inputErrorOnDark,
  inputIdle,
  inputIdleOnDark,
  inputSurface,
  inputSurfaceOnDark,
  label as labelClass,
  labelOnDark,
} from './ui'

export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'prefix'> {
  label: string
  /** Message shown under the field. Red when `invalid`, grey otherwise. */
  hint?: ReactNode
  invalid?: boolean
  /** `onDark` for a form laid directly on the hero gradient. */
  tone?: 'light' | 'onDark'
  /** Fixed prefix inside the field, e.g. the ₱ sign of a money input. */
  prefix?: ReactNode
  /** A control inside the right edge of the field, e.g. a show-password toggle. */
  suffix?: ReactNode
  /** Extra classes on the control itself, for a field-specific size or alignment. */
  inputClassName?: string
  /** React Hook Form hands out a ref for focusing the first invalid field. */
  inputRef?: Ref<HTMLInputElement>
}

const TONES = {
  light: {
    label: labelClass,
    surface: inputSurface,
    idle: inputIdle,
    error: inputError,
    hintIdle,
    hintError,
  },
  onDark: {
    label: labelOnDark,
    surface: inputSurfaceOnDark,
    idle: inputIdleOnDark,
    error: inputErrorOnDark,
    hintIdle: hintIdleOnDark,
    hintError: hintErrorOnDark,
  },
}

/**
 * A labelled text input with its message wired up for assistive technology:
 * `aria-invalid` plus `aria-describedby` pointing at the hint, so a screen
 * reader announces why the field was rejected.
 *
 * The `id` is generated unless one is passed, which lets several fields with
 * the same label coexist on one page (the transfer form has two account
 * numbers). Pass one only when a sibling control has to point at the field.
 */
export function Input({
  label,
  hint,
  invalid = false,
  tone = 'light',
  prefix,
  suffix,
  inputClassName,
  inputRef,
  id: idProp,
  ...rest
}: InputProps) {
  const generatedId = useId()
  const id = idProp ?? generatedId
  const describedBy = hint ? `${id}-hint` : undefined
  const hasPrefix = prefix !== null && prefix !== undefined && prefix !== ''
  const styles = TONES[tone]

  return (
    <div>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <div className="relative mt-1">
        {hasPrefix ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-text-subtle"
          >
            {prefix}
          </span>
        ) : null}
        <input
          ref={inputRef}
          id={id}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cx(
            inputClass,
            styles.surface,
            hasPrefix && 'pl-7',
            suffix ? 'pr-20' : null,
            invalid ? styles.error : styles.idle,
            inputClassName,
          )}
          {...rest}
        />
        {suffix ? (
          // top-1 matches the input's own mt-1, so the control spans the field exactly.
          <div className="absolute top-1 right-0 bottom-0 flex items-center">{suffix}</div>
        ) : null}
      </div>
      {hint ? (
        <p
          id={`${id}-hint`}
          // role="alert" only for errors. `aria-describedby` is read when focus
          // lands on the input, but an error that APPEARS after a failed submit
          // would otherwise stay silent; the alert role makes the insertion
          // announce itself. Neutral guidance ("at most two decimals") is
          // deliberately not an alert, so it never interrupts the user.
          role={invalid ? 'alert' : undefined}
          className={cx(hintClass, invalid ? styles.hintError : styles.hintIdle)}
        >
          {hint}
        </p>
      ) : null}
    </div>
  )
}

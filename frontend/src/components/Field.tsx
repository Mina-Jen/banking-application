import type { InputHTMLAttributes, ReactNode, Ref } from 'react'
import { useId } from 'react'
import { cx, hint as hintClass, input as inputClass, inputError, label as labelClass } from './ui'

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> {
  label: string
  /** Message shown under the field. Red when `invalid`, grey otherwise. */
  hint?: ReactNode
  invalid?: boolean
  /** React Hook Form hands out a ref for focusing the first invalid field. */
  inputRef?: Ref<HTMLInputElement>
}

/**
 * A labelled text input with its error wired up for assistive technology:
 * `aria-invalid` plus `aria-describedby` pointing at the message, so a screen
 * reader announces why the field was rejected.
 *
 * The `id` is generated, which lets several fields with the same label coexist
 * on one page (the transfer form has two account numbers).
 */
export function Field({ label, hint, invalid = false, inputRef, ...rest }: FieldProps) {
  const id = useId()
  const describedBy = hint ? `${id}-hint` : undefined

  return (
    <div>
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <input
        ref={inputRef}
        id={id}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        className={cx(inputClass, invalid && inputError)}
        {...rest}
      />
      {hint ? (
        <p
          id={`${id}-hint`}
          // role="alert" only for errors. `aria-describedby` is read when focus
          // lands on the input, but an error that APPEARS after a failed submit
          // would otherwise stay silent; the alert role makes the insertion
          // announce itself. Neutral guidance ("at most two decimals") is
          // deliberately not an alert, so it never interrupts the user.
          role={invalid ? 'alert' : undefined}
          className={cx(hintClass, invalid && 'font-medium text-red-700')}
        >
          {hint}
        </p>
      ) : null}
    </div>
  )
}

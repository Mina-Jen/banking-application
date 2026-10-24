import { useEffect, useId, useRef, useState } from 'react'
import { Input, type InputProps } from './Input'
import { cx } from './ui'

type PasswordInputProps = Omit<InputProps, 'type' | 'suffix' | 'id' | 'inputRef'>

/**
 * A password field with a Show / Hide toggle.
 *
 * The toggle's name comes from its text ("Show password"), not an aria-label,
 * so `getByLabel('Password')` still finds exactly one control: the field.
 * The field goes back to hidden whenever its form is submitted, so a browser
 * never stores the password as ordinary form text.
 */
export function PasswordInput({ tone = 'light', ...rest }: PasswordInputProps) {
  const id = useId()
  const ref = useRef<HTMLInputElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const form = ref.current?.form
    if (!form) {
      return
    }
    const hide = () => setVisible(false)
    form.addEventListener('submit', hide)
    return () => form.removeEventListener('submit', hide)
  }, [])

  return (
    <Input
      {...rest}
      id={id}
      tone={tone}
      type={visible ? 'text' : 'password'}
      inputRef={ref}
      suffix={
        <button
          type="button"
          aria-controls={id}
          onClick={() => setVisible((shown) => !shown)}
          className={cx(
            'inline-flex h-full min-w-16 items-center justify-center rounded-r-lg px-3 text-sm font-semibold',
            'focus-visible:outline-2 focus-visible:-outline-offset-2',
            tone === 'onDark'
              ? 'text-on-dark hover:bg-on-dark/10 focus-visible:outline-on-dark'
              : 'text-action hover:bg-action-soft hover:text-action-strong focus-visible:outline-action',
          )}
        >
          {visible ? 'Hide' : 'Show'}{' '}
          <span className="sr-only">password</span>
        </button>
      }
    />
  )
}

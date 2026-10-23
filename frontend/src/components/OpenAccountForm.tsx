import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert } from './Alert'
import { AmountField } from './AmountField'
import { Field } from './Field'
import { Spinner } from './Spinner'
import { buttonPrimary } from './ui'
import { bankingApi, type Account } from '../api/banking'
import { errorMessage } from '../api/errors'
import { normalizeAmount } from '../lib/money'
import { MAX_ACCOUNT_NAME_LENGTH } from '../lib/validation'
import { openAccountSchema, type OpenAccountValues } from '../forms/schemas'

/**
 * The "open an account" form on the dashboard.
 *
 * There is no account number field and no owner field on purpose. The backend
 * assigns the number itself, and takes the owner from the access token: a
 * customer's new account belongs to them, an admin's belongs to nobody. The
 * number arrives in the response, which is why the caller is handed the created
 * account rather than a message to write here.
 *
 * Validation comes from openAccountSchema, which allows a zero opening deposit
 * (the backend then records no transaction). There is no confirmation dialog
 * here by design: creating an account does not move money out of an existing
 * one, and the opening deposit is typed in full view immediately before submit.
 */
export function OpenAccountForm({ onCreated }: { onCreated: (account: Account) => void }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const {
    control,
    handleSubmit,
    reset,
  } = useForm<OpenAccountValues>({
    resolver: zodResolver(openAccountSchema),
    defaultValues: { accountName: '', initialDeposit: '' },
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  })

  async function onValid(values: OpenAccountValues) {
    setError(null)
    setSubmitting(true)
    try {
      const account = await bankingApi.createAccount({
        accountName: values.accountName.trim(),
        // Sent as a decimal string, padded to two places. Never a number.
        initialDeposit: normalizeAmount(values.initialDeposit),
      })
      reset()
      onCreated(account)
    } catch (cause) {
      // 422 = the backend rejected a field, 503 = it could not assign a number.
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit(onValid)} noValidate className="space-y-4">
      {error ? <Alert tone="error">{error}</Alert> : null}

      <Controller
        control={control}
        name="accountName"
        render={({ field, fieldState }) => (
          <Field
            label="Account holder name"
            value={field.value}
            inputRef={field.ref}
            onBlur={field.onBlur}
            onChange={(event) => field.onChange(event.target.value)}
            placeholder="Juan Dela Cruz"
            maxLength={MAX_ACCOUNT_NAME_LENGTH}
            disabled={submitting}
            invalid={fieldState.error !== undefined}
            hint={fieldState.error?.message}
          />
        )}
      />

      <Controller
        control={control}
        name="initialDeposit"
        render={({ field, fieldState }) => (
          <AmountField
            label="Opening deposit"
            value={field.value}
            inputRef={field.ref}
            onBlur={field.onBlur}
            onChange={field.onChange}
            disabled={submitting}
            error={fieldState.error?.message ?? null}
            hint="0 opens the account with no transaction."
          />
        )}
      />

      <p className="text-sm text-neutral-600">
        The bank assigns the account number when the account is opened.
      </p>

      <button type="submit" className={buttonPrimary} disabled={submitting}>
        {submitting ? <Spinner /> : null}
        {submitting ? 'Opening…' : 'Open account'}
      </button>
    </form>
  )
}

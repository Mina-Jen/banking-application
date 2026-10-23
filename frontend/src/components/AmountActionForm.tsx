import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert } from './Alert'
import { AmountField } from './AmountField'
import { ConfirmDialog } from './ConfirmDialog'
import { MovementSummary } from './MovementSummary'
import { Spinner } from './Spinner'
import { buttonPrimary } from './ui'
import type { Transaction } from '../api/types'
import { useIdempotencyKey } from '../api/useIdempotencyKey'
import { errorMessage } from '../api/errors'
import { formatPesos, normalizeAmount } from '../lib/money'
import { depositSchema, withdrawalSchema, type AmountValues } from '../forms/schemas'

interface AmountActionFormProps {
  kind: 'deposit' | 'withdraw'
  accountNumber: string
  /** Current balance, used by the withdrawal rule and the confirm summary. */
  availableBalance: string
  fieldLabel: string
  submitLabel: string
  busyLabel: string
  /** The API call. It receives the idempotency key this form is committed to. */
  run(amount: string, idempotencyKey: string): Promise<Transaction>
  onDone(transaction: Transaction): void
}

/**
 * The deposit and withdraw form. One component, because the two calls differ
 * only in the endpoint, the wording, and whether the amount may exceed the
 * balance.
 *
 * Two things are worth knowing. First, validation is a Zod schema shared with
 * the rest of the app (see forms/schemas.ts) and mirrored by the backend.
 * Second, the idempotency key is acquired when the user CONFIRMS, not when they
 * press the form button, and it is deliberately NOT released when the call
 * fails: if the browser timed out, the deposit may already be recorded, and
 * retrying with the same key is the only way to find out without risking a
 * second deposit. The key is released on success and whenever the amount is
 * edited, because a different amount is a different payment.
 */
export function AmountActionForm({
  kind,
  accountNumber,
  availableBalance,
  fieldLabel,
  submitLabel,
  busyLabel,
  run,
  onDone,
}: AmountActionFormProps) {
  const schema = useMemo(
    () => (kind === 'withdraw' ? withdrawalSchema(availableBalance) : depositSchema),
    [kind, availableBalance],
  )

  const {
    control,
    handleSubmit,
    reset,
  } = useForm<AmountValues>({
    resolver: zodResolver(schema),
    defaultValues: { amount: '' },
    // Judge only after the first submit; then re-check as the user types.
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  })

  /** Set once the values pass validation; it is what the dialog shows. */
  const [pending, setPending] = useState<AmountValues | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const idempotency = useIdempotencyKey()

  function onValid(values: AmountValues) {
    setError(null)
    setPending(values)
  }

  async function confirm() {
    if (pending === null) {
      return
    }
    setBusy(true)
    setError(null)

    const key = idempotency.acquire()
    try {
      const transaction = await run(normalizeAmount(pending.amount), key)
      // Committed: release the key so the next payment gets its own, and clear
      // the form.
      idempotency.reset()
      reset({ amount: '' })
      setPending(null)
      setDone(
        `${formatPesos(transaction.amount)} recorded. Reference ${transaction.reference}. ` +
          `New balance ${formatPesos(transaction.balanceAfter)}.`,
      )
      onDone(transaction)
    } catch (cause) {
      // Keep `key` so pressing the button again replays rather than double-pays.
      setError(errorMessage(cause))
      setPending(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit(onValid)} noValidate className="space-y-4">
        {error ? <Alert tone="error">{error}</Alert> : null}
        {done ? <Alert tone="success">{done}</Alert> : null}

        <Controller
          control={control}
          name="amount"
          render={({ field, fieldState }) => (
            <AmountField
              label={fieldLabel}
              value={field.value}
              disabled={busy}
              inputRef={field.ref}
              onBlur={field.onBlur}
              onChange={(next) => {
                // A different amount is a different payment, so it must not
                // inherit the previous key.
                idempotency.reset()
                setDone(null)
                field.onChange(next)
              }}
              error={fieldState.error?.message ?? null}
            />
          )}
        />

        <button type="submit" className={buttonPrimary} disabled={busy}>
          {busy ? <Spinner /> : null}
          {busy ? busyLabel : submitLabel}
        </button>
      </form>

      <ConfirmDialog
        open={pending !== null}
        title={kind === 'deposit' ? 'Confirm deposit' : 'Confirm withdrawal'}
        description="This moves money immediately and cannot be undone from the app."
        confirmLabel={
          pending ? `${submitLabel} ${formatPesos(normalizeAmount(pending.amount))}` : submitLabel
        }
        busyLabel={busyLabel}
        busy={busy}
        tone={kind === 'withdraw' ? 'danger' : 'primary'}
        onConfirm={() => void confirm()}
        onCancel={() => setPending(null)}
      >
        <MovementSummary
          rows={[
            {
              label: 'Amount',
              value: formatPesos(normalizeAmount(pending?.amount ?? '0')),
              strong: true,
            },
            {
              label: kind === 'deposit' ? 'To account' : 'From account',
              value: accountNumber,
              mono: true,
            },
            { label: 'Current balance', value: formatPesos(availableBalance) },
          ]}
        />
      </ConfirmDialog>
    </>
  )
}

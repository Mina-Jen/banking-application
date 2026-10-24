import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert } from './Alert'
import { Button } from './Button'
import { MoneyInput } from './MoneyInput'
import { ConfirmDialog } from './ConfirmDialog'
import { Input } from './Input'
import { MovementSummary } from './MovementSummary'
import type { Transaction } from '../api/types'
import { useIdempotencyKey } from '../api/useIdempotencyKey'
import { errorMessage } from '../api/errors'
import { formatPesos, normalizeAmount } from '../lib/money'
import { MAX_ACCOUNT_NUMBER_LENGTH } from '../lib/validation'
import { transferSchema, type TransferValues } from '../forms/schemas'

interface TransferFormProps {
  /** The account the money leaves; fixed, and shown read-only. */
  sourceAccountNumber: string
  /** Current balance, used by the transfer rule and the confirm summary. */
  availableBalance: string
  run(
    input: {
      sourceAccountNumber: string
      destinationAccountNumber: string
      amount: string
    },
    idempotencyKey: string,
  ): Promise<Transaction>
  onDone(transaction: Transaction): void
  /** A dialog host can move result messages into a page-level toast. */
  feedback?: 'inline' | 'external'
  onError?(message: string): void
}

/**
 * Transfer form. The source account comes from the page, not from a field the
 * user can edit, so it is impossible to submit a transfer out of an account
 * other than the one being viewed.
 *
 * The destination may belong to anyone — the backend only checks ownership of
 * the source, so the confirm dialog spells the destination out before anything
 * is sent. The key lifecycle is the same as AmountActionForm: acquired on
 * confirm, held across a failed attempt, released on success, released when any
 * field is edited.
 */
export function TransferForm({
  sourceAccountNumber,
  availableBalance,
  run,
  onDone,
  feedback = 'inline',
  onError,
}: TransferFormProps) {
  const schema = useMemo(
    () => transferSchema(sourceAccountNumber, availableBalance),
    [sourceAccountNumber, availableBalance],
  )

  const {
    control,
    handleSubmit,
    reset,
  } = useForm<TransferValues>({
    resolver: zodResolver(schema),
    defaultValues: { destinationAccountNumber: '', amount: '' },
    mode: 'onSubmit',
    reValidateMode: 'onChange',
  })

  const [pending, setPending] = useState<TransferValues | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const idempotency = useIdempotencyKey()

  function invalidateKey() {
    // Editing any field makes this a different payment.
    idempotency.reset()
    setDone(null)
  }

  function onValid(values: TransferValues) {
    setError(null)
    setPending(values)
  }

  async function confirm() {
    if (pending === null) {
      return
    }
    setBusy(true)
    setError(null)

    const destination = pending.destinationAccountNumber.trim()
    const key = idempotency.acquire()
    try {
      const transaction = await run(
        {
          sourceAccountNumber,
          destinationAccountNumber: destination,
          amount: normalizeAmount(pending.amount),
        },
        key,
      )
      idempotency.reset()
      reset({ destinationAccountNumber: '', amount: '' })
      setPending(null)
      setDone(
        `${formatPesos(transaction.amount)} sent to ${transaction.referenceAccount ?? destination}. ` +
          `Reference ${transaction.reference}. New balance ${formatPesos(transaction.balanceAfter)}.`,
      )
      onDone(transaction)
    } catch (cause) {
      // Keep `key` so a retry replays instead of transferring twice.
      const message = errorMessage(cause)
      setError(message)
      onError?.(message)
      setPending(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit(onValid)} noValidate className="space-y-4">
        {feedback === 'inline' && error ? <Alert tone="error">{error}</Alert> : null}
        {feedback === 'inline' && done ? <Alert tone="success">{done}</Alert> : null}

        <Input
          label="From account"
          value={sourceAccountNumber}
          readOnly
          disabled
          hint="The account you are viewing."
        />

        <Controller
          control={control}
          name="destinationAccountNumber"
          render={({ field, fieldState }) => (
            <Input
              label="To account number"
              value={field.value}
              inputRef={field.ref}
              onBlur={field.onBlur}
              onChange={(event) => {
                invalidateKey()
                field.onChange(event.target.value)
              }}
              placeholder="200002"
              maxLength={MAX_ACCOUNT_NUMBER_LENGTH}
              disabled={busy}
              invalid={fieldState.error !== undefined}
              hint={fieldState.error?.message ?? 'May belong to another customer.'}
            />
          )}
        />

        <Controller
          control={control}
          name="amount"
          render={({ field, fieldState }) => (
            <MoneyInput
              label="Amount"
              value={field.value}
              inputRef={field.ref}
              onBlur={field.onBlur}
              disabled={busy}
              onChange={(next) => {
                invalidateKey()
                field.onChange(next)
              }}
              error={fieldState.error?.message ?? null}
            />
          )}
        />

        <Button type="submit" loading={busy}>
          {busy ? 'Transferring…' : 'Transfer'}
        </Button>
      </form>

      <ConfirmDialog
        open={pending !== null}
        title="Confirm transfer"
        description="This moves money immediately and cannot be undone from the app."
        confirmLabel={
          pending ? `Transfer ${formatPesos(normalizeAmount(pending.amount))}` : 'Transfer'
        }
        busyLabel="Transferring…"
        busy={busy}
        tone="danger"
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
            { label: 'From account', value: sourceAccountNumber, mono: true },
            {
              label: 'To account',
              value: pending?.destinationAccountNumber.trim() ?? '',
              mono: true,
            },
            { label: 'Current balance', value: formatPesos(availableBalance) },
          ]}
        />
      </ConfirmDialog>
    </>
  )
}

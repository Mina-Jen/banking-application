import { useRef, type ReactNode } from 'react'
import { Button } from './Button'
import { Sheet } from './Sheet'

interface ConfirmDialogProps {
  open: boolean
  title: string
  /** Optional one-line explanation under the title. */
  description?: string
  /** Wording of the action button, e.g. "Deposit ₱25.00". */
  confirmLabel: string
  /** Shown while the request is in flight; defaults to confirmLabel. */
  busyLabel?: string
  busy?: boolean
  /** Red styling for an action that takes money out. */
  tone?: 'primary' | 'danger'
  onConfirm(): void
  onCancel(): void
  /** What is about to happen, rendered between the text and the buttons. */
  children?: ReactNode
}

/**
 * A modal confirmation step for anything that moves money.
 *
 * The mechanics — focus trap, Escape, scroll lock, backdrop, `aria-modal` — are
 * <Sheet>'s; this component only decides the wording and which button is red.
 *
 * Focus lands on Cancel deliberately: a fast second Enter on the keyboard then
 * cancels instead of confirming a payment.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  busyLabel,
  busy = false,
  tone = 'primary',
  onConfirm,
  onCancel,
  children,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null)

  return (
    <Sheet
      open={open}
      title={title}
      description={description}
      busy={busy}
      layer="confirmation"
      onDismiss={onCancel}
      initialFocusRef={cancelRef}
      footer={
        <div className="mt-6 flex justify-end gap-3">
          <Button ref={cancelRef} variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : 'primary'}
            onClick={onConfirm}
            disabled={busy}
            loading={busy}
          >
            {busy ? (busyLabel ?? confirmLabel) : confirmLabel}
          </Button>
        </div>
      }
    >
      {children ? <div className="mt-4">{children}</div> : null}
    </Sheet>
  )
}

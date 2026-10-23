import { useEffect, useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Spinner } from './Spinner'
import { buttonDanger, buttonPrimary, buttonSecondary, cx } from './ui'

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
 * Written by hand rather than with <dialog> because jsdom does not implement
 * showModal(), which would make the behaviour untestable. It covers what the
 * native element would otherwise give us:
 *
 *   - role="dialog" + aria-modal, with the title and description wired up;
 *   - Escape closes, Tab is trapped inside the dialog;
 *   - the backdrop click closes, but clicks on the panel do not;
 *   - focus moves to CANCEL on open and returns to the trigger on close;
 *   - the page behind cannot scroll while it is open.
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
  const titleId = useId()
  const descriptionId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)
  const restoreFocusTo = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    restoreFocusTo.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    cancelRef.current?.focus()

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
      // The trigger is usually a button in the form; without this the user is
      // dropped back at the top of the document.
      restoreFocusTo.current?.focus()
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        if (busy) {
          return
        }
        event.preventDefault()
        onCancel()
        return
      }
      if (event.key !== 'Tab' || panelRef.current === null) {
        return
      }
      // Keep Tab inside the dialog: wrap from the last focusable to the first
      // and back.
      const focusable = Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        ),
      )
      if (focusable.length === 0) {
        event.preventDefault()
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      const active = document.activeElement
      if (event.shiftKey && active === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, busy, onCancel])

  if (!open) {
    return null
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      // mousedown rather than click: a drag that ends here should not confirm.
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) {
          onCancel()
        }
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        aria-busy={busy || undefined}
        tabIndex={-1}
        className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl ring-1 ring-slate-200"
      >
        <h2 id={titleId} className="text-lg font-semibold text-slate-900">
          {title}
        </h2>
        {description ? (
          <p id={descriptionId} className="mt-1 text-sm text-slate-600">
            {description}
          </p>
        ) : null}

        {children ? <div className="mt-4">{children}</div> : null}

        <div className="mt-6 flex justify-end gap-3">
          <button
            ref={cancelRef}
            type="button"
            className={buttonSecondary}
            onClick={onCancel}
            disabled={busy}
          >
            Cancel
          </button>
          <button
            type="button"
            className={cx(tone === 'danger' ? buttonDanger : buttonPrimary)}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? <Spinner /> : null}
            {busy ? (busyLabel ?? confirmLabel) : confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

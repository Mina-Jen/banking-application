import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'
import { IconClose } from './Icon'
import { cx, focusRing, heading, subheading } from './ui'

interface SheetProps {
  open: boolean
  title: string
  description?: string
  children?: ReactNode
  /** The foot of the panel. The sheet never decides what its own actions are. */
  footer?: ReactNode
  /**
   * While a request is in flight the panel cannot be dismissed by Escape or by
   * the backdrop, and it says so with `aria-busy`. A dismissal that leaves a
   * transfer running is worse than no dismissal at all.
   */
  busy?: boolean
  /**
   * `bottom` slides up from the edge of the screen — the phone idiom, and the
   * right one for a list of choices. `center` is for a decision that stops the
   * user, such as confirming a money movement.
   */
  variant?: 'center' | 'bottom'
  layer?: 'base' | 'confirmation'
  onDismiss(): void
  /**
   * Where focus lands. For a confirmation that is the cancel button, so a second
   * Enter press backs out instead of committing money.
   */
  initialFocusRef?: RefObject<HTMLElement | null>
  className?: string
}

/**
 * The modal layer everything else is built on: a scrim, a panel, and the five
 * things a hand-rolled modal usually forgets.
 *
 * Written by hand rather than with <dialog> because jsdom does not implement
 * showModal(), which would leave the behaviour untested. It covers what the
 * native element would otherwise give us:
 *
 *   - role="dialog" + aria-modal, with the title and description wired up;
 *   - Escape closes, Tab is trapped inside the panel;
 *   - a backdrop press closes, but a press on the panel does not;
 *   - focus moves in on open and back to the trigger on close;
 *   - the page behind cannot scroll while it is open.
 *
 * The backdrop is the portal's own element and the panel is its direct child.
 * Tests reach the backdrop as `dialog.parentElement`, so that shape is part of
 * the contract.
 */
export function Sheet({
  open,
  title,
  description,
  children,
  footer,
  busy = false,
  variant = 'center',
  layer = 'base',
  onDismiss,
  initialFocusRef,
  className,
}: SheetProps) {
  const titleId = useId()
  const descriptionId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const restoreFocusTo = useRef<HTMLElement | null>(null)
  const isBottom = variant === 'bottom'

  useEffect(() => {
    if (!open) {
      return
    }
    restoreFocusTo.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    if (initialFocusRef?.current) {
      initialFocusRef.current.focus()
    } else {
      panelRef.current?.focus()
    }

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
      // The trigger is usually a button in the form; without this the user is
      // dropped back at the top of the document.
      restoreFocusTo.current?.focus()
    }
  }, [open, initialFocusRef])

  useEffect(() => {
    if (!open) {
      return
    }

    function onKeyDown(event: KeyboardEvent) {
      // A money form may open its confirmation over an action sheet. Only the
      // last dialog in the portal stack may handle Escape or trap Tab; otherwise
      // one Escape would dismiss both layers.
      const dialogs = document.querySelectorAll('[role="dialog"]')
      if (dialogs[dialogs.length - 1] !== panelRef.current) {
        return
      }
      if (event.key === 'Escape') {
        if (busy) {
          return
        }
        event.preventDefault()
        onDismiss()
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
  }, [open, busy, onDismiss])

  if (!open) {
    return null
  }

  return createPortal(
    <div
      className={cx(
        'sheet-backdrop fixed inset-0 flex justify-center bg-ink-950/60 p-0 sm:p-4',
        layer === 'confirmation' ? 'z-[70]' : 'z-50',
        isBottom ? 'items-end' : 'items-center',
      )}
      // mousedown rather than click: a drag that ends here should not confirm.
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) {
          onDismiss()
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
        className={cx(
          'sheet-panel max-h-[86dvh] w-full overflow-y-auto bg-surface p-6 shadow-xl ring-1 ring-line outline-none',
          isBottom
            ? 'rounded-t-2xl sm:max-w-md sm:rounded-xl'
            : 'max-w-md rounded-xl',
          className,
        )}
      >
        {isBottom ? (
          <span
            aria-hidden="true"
            className="mx-auto mb-4 block h-1 w-10 rounded-full bg-line-strong sm:hidden"
          />
        ) : null}

        <div className={cx('flex items-start gap-4', description ? 'mb-1' : 'mb-4')}>
          <div className="min-w-0 flex-1">
            <h2 id={titleId} className={heading}>
              {title}
            </h2>
          </div>
          {/* Only the sheet gets an X. A centred dialog is a decision with named
              outcomes, and a cross that quietly means "cancel" would be a second
              word for the same thing — and a new first stop for the Tab key. */}
          {isBottom ? (
            <button
              type="button"
              onClick={onDismiss}
              disabled={busy}
              aria-label="Close"
              className={cx(
                '-m-2 flex h-11 w-11 items-center justify-center rounded-full text-text-subtle hover:bg-surface-muted hover:text-text',
                focusRing,
              )}
            >
              <IconClose className="h-5 w-5" />
            </button>
          ) : null}
        </div>

        {description ? (
          <p id={descriptionId} className={cx(subheading, 'mb-4')}>
            {description}
          </p>
        ) : null}

        {children}
        {footer}
      </div>
    </div>,
    document.body,
  )
}

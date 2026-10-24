import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatPesos } from '../lib/money'
import { IconCheck, IconCopy } from './Icon'
import { Logo } from './Logo'
import {
  accountNumber as accountNumberStyle,
  buttonClasses,
  cx,
  eyebrowOnDark,
  focusRingOnDark,
  moneyDisplay,
} from './ui'

interface GradientAccountCardProps {
  /** Full account number. It is masked on screen and only ever leaves through the copy button. */
  accountNumber: string
  accountName: string
  /** Balance as the decimal string the backend sent. Omitted while it loads. */
  balance?: string
  /** Product line shown in the corner. The app has no card types yet, so this is wording, not data. */
  kind?: string
  /** Optional account-detail destination shown as a separate control beside Copy. */
  manageTo?: string
  className?: string
}

/**
 * Last four digits, the convention every card face follows. The masked groups
 * are printed at 55% white — 5.2:1 on this surface, against 12.2:1 for the
 * digits — so the eye lands on the four it can read instead of on eight marks
 * of equal weight.
 */
function mask(accountNumber: string) {
  return (
    <>
      <span className="text-on-dark/55">•••• </span>
      {accountNumber.slice(-4)}
    </>
  )
}

/**
 * The account as an object: the depth gradient, the grain, and the number you
 * can hand to someone without exposing it on a shared screen.
 *
 * Copy is the escape hatch for the mask, and the reveal is the escape hatch for
 * a browser that refuses clipboard access — an insecure origin or a denied
 * permission both look identical to a broken button until the number comes back.
 */
export function GradientAccountCard({
  accountNumber,
  accountName,
  balance,
  kind = 'Debit',
  manageTo,
  className,
}: GradientAccountCardProps) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'unavailable'>('idle')

  // The confirmation is a state, not a permanent label: it has to expire or the
  // button stops telling the truth about the next press.
  useEffect(() => {
    if (status === 'idle') {
      return
    }
    const timer = setTimeout(() => setStatus('idle'), 2400)
    return () => clearTimeout(timer)
  }, [status])

  async function copy() {
    if (!navigator.clipboard) {
      setStatus('unavailable')
      return
    }
    try {
      await navigator.clipboard.writeText(accountNumber)
      setStatus('copied')
    } catch {
      setStatus('unavailable')
    }
  }

  return (
    <section
      className={cx(
        'hero-card relative isolate overflow-hidden rounded-card p-5 shadow-elevate-2 sm:p-6',
        className,
      )}
      aria-label={`${accountName}, ${kind} account ending ${accountNumber.slice(-4)}`}
    >
      <div className="flex items-start justify-between gap-4">
        <p className={eyebrowOnDark}>{`Sinag · ${kind}`}</p>
        <Logo size={26} variant="dark" />
      </div>

      <p className={cx(accountNumberStyle, 'mt-9 text-display-md font-bold text-on-dark')}>
        {status === 'unavailable' ? accountNumber : mask(accountNumber)}
      </p>

      <div className="mt-6 flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
        <div className="min-w-0">
          <p className={eyebrowOnDark}>Balance</p>
          <p className={cx(moneyDisplay, 'mt-1 text-display-md font-bold text-on-dark')}>
            {balance ? formatPesos(balance) : '—'}
          </p>
          <p className="mt-2 truncate text-sm text-on-dark-muted">{accountName}</p>
        </div>

        <div className="flex flex-wrap gap-2">
          {manageTo ? (
            <Link to={manageTo} className={buttonClasses('onDarkOutline', 'sm')}>
              Manage
            </Link>
          ) : null}
          <button
            type="button"
            onClick={() => void copy()}
            className={cx(
              'press inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-xs font-semibold',
              'bg-on-dark text-ink-900 hover:bg-surface-muted',
              focusRingOnDark,
            )}
          >
            {status === 'copied' ? <IconCheck className="h-4 w-4" /> : <IconCopy className="h-4 w-4" />}
            {status === 'copied'
              ? 'Copied'
              : status === 'unavailable'
                ? 'Number shown'
                : 'Copy number'}
          </button>
        </div>
      </div>

      {/* Announced, not just shown: the copy either worked or it did not, and a
          sighted-only confirmation is no confirmation. */}
      <p aria-live="polite" className="sr-only">
        {status === 'copied'
          ? 'Account number copied.'
          : status === 'unavailable'
            ? 'Copying is unavailable, so the full number is shown instead.'
            : ''}
      </p>
    </section>
  )
}

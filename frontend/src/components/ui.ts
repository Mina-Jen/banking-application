/**
 * Shared Tailwind class strings, so a button or an input looks the same on
 * every page and a style change happens in one place.
 */

/** Joins class names, dropping falsy values. */
export function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

export const card = 'rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200'

export const label = 'block text-sm font-medium text-slate-700'

export const input =
  'mt-1 block w-full rounded-lg bg-white px-3 py-2 text-slate-900 ring-1 ring-slate-300 ' +
  'placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-600 ' +
  'disabled:cursor-not-allowed disabled:bg-slate-50'

export const inputError = 'ring-red-400 focus:ring-red-600'

export const hint = 'mt-1 text-xs text-slate-500'

export const buttonPrimary =
  'inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-700 px-4 py-2 ' +
  'text-sm font-semibold text-white transition hover:bg-emerald-800 ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600 focus-visible:ring-offset-2 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

export const buttonSecondary =
  'inline-flex items-center justify-center gap-2 rounded-lg bg-white px-4 py-2 ' +
  'text-sm font-semibold text-slate-700 ring-1 ring-slate-300 transition hover:bg-slate-50 ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

/** For an action that takes money out of an account. */
export const buttonDanger =
  'inline-flex items-center justify-center gap-2 rounded-lg bg-red-700 px-4 py-2 ' +
  'text-sm font-semibold text-white transition hover:bg-red-800 ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

export const tableHead = 'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500'

export const tableCell = 'px-4 py-3 text-sm text-slate-700'

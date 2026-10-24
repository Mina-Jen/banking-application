/**
 * Shared Tailwind class strings, so a button or an input looks the same on
 * every page and a style change happens in one place.
 *
 * Everything here is built from the tokens declared in `index.css` — no raw
 * colour, size or radius value appears in a component. If you cannot find a
 * token for what you need, add one there rather than typing a hex value here.
 */

/** Joins class names, dropping falsy values. */
export function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

/**
 * The keyboard-only ring, outline rather than ring so it survives an `overflow`
 * clip and stays visible next to a white card edge. `outline-offset` keeps the
 * gap, so the ring never fuses with the control's own border.
 */
export const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-action'

/** Same ring where it sits on the dark end of the gradient. */
export const focusRingOnDark =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-on-dark'

// ---------------------------------------------------------------- surfaces

export const card = 'rounded-xl bg-surface p-6 shadow-sm ring-1 ring-line'

/** A card raised one step further: used where a sheet overlaps the hero. */
export const cardRaised = 'rounded-xl bg-surface p-6 shadow-lg ring-1 ring-line'

/** Hairline separator between rows of a card or list. */
export const divider = 'divide-y divide-line'

// -------------------------------------------------------------------- text

export const heading = 'text-display-md font-bold text-text'

export const subheading = 'text-sm text-text-muted'

/** Uppercase label that sits above a display headline. */
export const eyebrow =
  'text-xs font-bold uppercase tracking-[0.18em] text-text-subtle'

export const eyebrowOnDark =
  'text-xs font-bold uppercase tracking-[0.18em] text-on-dark-muted'

export const label = 'block text-sm font-semibold text-text-muted'

/** An inline text link on the gradient: underlined, because colour alone cannot mark it there. */
export const linkOnDark = cx(
  'rounded-sm font-semibold text-on-dark underline decoration-on-dark/50 underline-offset-2 hover:decoration-on-dark',
  focusRingOnDark,
)

/**
 * The message under a field. Its colour comes from `hintIdle` or `hintError`,
 * never from here: Tailwind sorts two same-property utilities by theme order,
 * not by the order they were written, so stacking `text-text-subtle` with
 * `text-danger` silently keeps the grey and the error looks like guidance.
 */
export const hint = 'mt-1 text-xs'

export const hintIdle = 'text-text-subtle'

export const hintError = 'font-semibold text-danger'

/** The same three on the dark end of the gradient, for a form laid on the hero. */
export const labelOnDark = 'block text-sm font-semibold text-on-dark'

export const hintIdleOnDark = 'text-on-dark-muted'

export const hintErrorOnDark = 'font-semibold text-danger-on-dark'

// ------------------------------------------------------------------ inputs

/**
 * `min-h-11` is 44px, the smallest comfortable tap target, so the same field
 * works on a phone without a media query.
 *
 * Fill, text colour, border and focus ring colour are deliberately NOT here for
 * the same reason as the hint: exactly one surface class and one state class
 * below are applied, never two that set the same property.
 *
 * No `focus:outline-none` here: in Tailwind v4 it sets the shared outline-style
 * variable to `none`, which the `focus-visible:outline-2` ring then inherits,
 * so the field silently loses its keyboard ring.
 */
export const input =
  'mt-1 block min-h-11 w-full rounded-lg px-3 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 ' +
  'disabled:cursor-not-allowed'

export const inputSurface =
  'bg-surface text-text placeholder:text-ink-400 disabled:bg-surface-muted disabled:text-text-subtle'

/**
 * On the gradient the field is a tinted window, not a white slab: the white
 * pill under it has to stay the one solid white shape on the page, or it reads
 * as a third field. `color-scheme: dark` makes browser autofill paint a dark
 * field too, instead of a pale yellow one with the white text on it.
 */
export const inputSurfaceOnDark =
  'bg-on-dark/8 text-on-dark [color-scheme:dark] placeholder:text-on-dark-muted ' +
  'disabled:bg-transparent disabled:text-on-dark-muted'

export const inputIdle = 'ring-1 ring-field-border focus-visible:outline-action'

export const inputError = 'ring-1 ring-danger focus-visible:outline-danger'

/** Half-white outline: at least 3:1 against the brightest part of the scrim. */
export const inputIdleOnDark = 'ring-1 ring-on-dark/50 hover:ring-on-dark/80 focus-visible:outline-on-dark'

/** A 1px red line is lost on a dark page, so the error ring is doubled there. */
export const inputErrorOnDark = 'ring-2 ring-danger-on-dark focus-visible:outline-on-dark'

/** Native select with the same light-surface geometry and focus treatment as Input. */
export const select = cx(input, inputSurface, inputIdle, 'appearance-auto')

// ----------------------------------------------------------------- buttons

/**
 * Shared geometry for every variant: 44px tall, one line of semibold type, and
 * a press state that scales the pill without moving its neighbours.
 *
 * A busy button is disabled too, but it is working rather than unavailable, so
 * it keeps its full colour and shows a progress cursor instead of fading out.
 */
export const buttonBase =
  'press inline-flex min-h-11 items-center justify-center gap-2 rounded-full px-5 ' +
  'text-sm font-semibold disabled:[transform:none] ' +
  'disabled:not-aria-busy:cursor-not-allowed disabled:not-aria-busy:opacity-50 aria-busy:cursor-progress'

/** The main action on a light surface. */
export const buttonPrimary = cx(
  buttonBase,
  'bg-action text-on-dark hover:bg-action-strong',
  focusRing,
  'disabled:hover:bg-action',
)

/** A secondary action on a light surface: same weight, no fill. */
export const buttonSecondary = cx(
  buttonBase,
  'bg-surface text-text-muted ring-1 ring-field-border hover:bg-action-soft hover:text-action-strong',
  focusRing,
)

/** For an action that takes money out of an account. */
export const buttonDanger = cx(
  buttonBase,
  'bg-danger text-on-dark hover:bg-danger-strong',
  focusRing,
  'disabled:hover:bg-danger',
)

/** The main action on the dark end of the gradient: a white pill. */
export const buttonOnDark = cx(
  buttonBase,
  'bg-on-dark text-ink-900 hover:bg-surface-muted',
  focusRingOnDark,
)

/** A secondary action on the gradient: outlined, no fill. */
export const buttonOnDarkOutline = cx(
  buttonBase,
  'text-on-dark ring-1 ring-on-dark/50 hover:bg-depth-scrim/40',
  focusRingOnDark,
)

/** Bare text that still reads as a control. */
export const buttonGhost = cx(
  buttonBase,
  'rounded-md bg-transparent px-3 text-text-muted hover:bg-action-soft hover:text-action-strong',
  focusRing,
)

export const buttonSmall = 'min-h-9 px-3 text-xs'

export type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'danger'
  | 'ghost'
  /** White pill for the dark end of a gradient. */
  | 'onDark'
  | 'onDarkOutline'

export type ButtonSize = 'md' | 'sm'

const VARIANTS: Record<ButtonVariant, string> = {
  primary: buttonPrimary,
  secondary: buttonSecondary,
  danger: buttonDanger,
  ghost: buttonGhost,
  onDark: buttonOnDark,
  onDarkOutline: buttonOnDarkOutline,
}

/**
 * The class string behind `<Button>`. Use this when the element cannot be a
 * button — a `<Link to=…>` styled as the primary action, for instance — so a
 * link and a button with the same variant are indistinguishable.
 */
export function buttonClasses(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'md',
  className?: string,
): string {
  return cx(VARIANTS[variant], size === 'sm' && buttonSmall, className)
}

// ------------------------------------------------------------------ tables

export const tableHead =
  'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-text-subtle'

export const tableCell = 'px-4 py-3 text-sm text-text-muted'

// ------------------------------------------------------- gradient surfaces

/** The hero surface: depth gradient, scrim and grain. See index.css. */
export const hero = 'hero-gradient'

/** Display headline — pair a size with it: `cx('text-display-xl', display)`. */
export const display = 'display'

/**
 * Money. `tabular-nums` stops the digits shifting as a balance changes; the
 * alignment is left to the caller so a total can sit on the right of a row and a
 * label on the left of one without fighting a shared class.
 */
export const money = 'tabular-nums'

/** Account numbers are digits too, and they read better slightly apart. */
export const accountNumber = 'tabular-nums tracking-[0.08em]'

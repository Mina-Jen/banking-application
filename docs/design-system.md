# Sinag — design system

The visual rules for the React frontend: what colours exist, what they mean, how
type is set, and which component to reach for. Two files implement it:

| File | Role |
| --- | --- |
| [`frontend/src/index.css`](../frontend/src/index.css) | The theme. Tailwind v4 is configured in CSS, so every token below is a real utility (`bg-action`, `text-display-md`). |
| [`frontend/src/components/ui.ts`](../frontend/src/components/ui.ts) | Shared class strings assembled from those tokens, plus the component primitives that use them. |

There is no `tailwind.config.*` file. Do not add one.

**Scope.** The tokens, the primitives and the page layouts are all on this
system: the dashboard, the account statement, the admin overview, the review
queue and the two auth screens. What a page may not do is introduce a value that
is not a token — see [The pages today](#6-the-pages-today).

---

## 1. Colour

Three ramps and one set of semantic roles. Components name the **role**, never a
ramp step: `bg-surface`, not `bg-ink-50`.

### Depth — the hero gradient

Read left to right as money sinking into an account: cyan, blue, indigo,
near-black. These four stops exist to be *gradient stops*.

| Token | Hex |
| --- | --- |
| `--color-depth-1` | `#5fe3ef` |
| `--color-depth-2` | `#2f83f0` |
| `--color-depth-3` | `#2b2470` |
| `--color-depth-4` | `#060b18` |
| `--color-depth-veil` | `#080c2e` |

The veil is indigo, not black. A neutral shadow over cyan reads as muddy grey;
a hue-preserving one reads as water. That single choice is what lets the ramp
stay visible at the alphas the next section lists.

**Never paint a depth stop and write on it.** Bare, white text is 1.53:1 on
`depth-1` and 3.73:1 on `depth-2` — both fail. Use `.hero-gradient` (or
`.hero-card`, [below](#2-the-hero-surface)), which adds the veil.

### Brand — the same ramp as a usable scale

`--color-brand-50` `#eef6ff` through `--color-brand-950` `#12203f`, with
`brand-600` `#1c63d6` and `brand-700` `#1a4fae` as the two steps that carry text.
`brand-500` is 3.7:1 on white: large type, icons and fills only, never body copy.

### Ink — cool neutrals

`--color-ink-50` `#f8fafc` … `--color-ink-950` `#020617`. These keep the exact
values the app used before the tokens existed, so adopting one is a rename, not a
re-render.

### Semantic roles

| Token | Hex | Use |
| --- | --- | --- |
| `canvas` | `#f1f5f9` | Page background |
| `surface` | `#ffffff` | Cards, sheets, inputs |
| `surface-muted` | `#f8fafc` | Recessed rows, hover fills |
| `line` | `#e2e8f0` | Hairlines, card borders |
| `line-strong` | `#cbd5e1` | A line that has to be seen |
| `field-border` | `#7f93ab` | Input boundary |
| `text` | `#0f172a` | Body and headings |
| `text-muted` | `#475569` | Secondary copy |
| `text-subtle` | `#576779` | Hints, table headers |
| `on-dark` | `#ffffff` | Text on the gradient |
| `on-dark-muted` | `#cbd5e1` | Eyebrows and secondary text on the gradient |

`field-border` is the lightest grey that still clears **3:1 against white** — a
form boundary is a non-text control, so it needs 3:1, not 4.5:1. Anything lighter
is decoration and belongs to `line`.

### Action and money semantics

The plain step doubles as a fill with white on it *and* as text on white, which
is what keeps the palette this small. `-strong` is for text sitting on a `-soft`
tint.

| | Fill + white text | Text on white | On its `-soft` tint |
| --- | --- | --- | --- |
| **action** `#1c63d6` / `#1a4fae` / `#eef6ff` | 5.52 | 5.52 | 6.94 (`-strong`) |
| **success** `#047857` / `#065f46` / `#ecfdf5` | 5.48 | 5.48 | 7.29 (`-strong`) |
| **danger** `#b91c1c` / `#991b1b` / `#fef2f2` | 6.47 | 6.47 | 7.60 (`-strong`) |
| **warning** `#b45309` / `#92400e` / `#fffbeb` | 5.02 | 5.02 | 6.84 (`-strong`) |

Every pair above clears WCAG 2.2 AA (4.5:1 for body text). Measured with the
relative-luminance formula, not estimated — see [Verifying](#7-verifying).

`danger-on-dark` `#fecaca` is error text on the gradient, where `danger` is about
2:1. Measured against the brightest rendered pixel of the auth form column
(`rgb(36,53,90)`): error text 8.39, muted hint 8.17, white label 12.14, and the
half-white field outline 3.91 (non-text, needs 3:1).

### Light-surface text

| Pair | Ratio |
| --- | --- |
| `text` on `surface` / `canvas` | 17.85 / 16.30 |
| `text-muted` on `surface` / `canvas` | 7.58 / 6.92 |
| `text-subtle` on `surface` / `canvas` / `surface-muted` | 5.80 / 5.29 / 5.54 |

`text-subtle` is the floor of the system at 5.29. Do not lighten it further.

---

## 2. The hero surface

`.hero-gradient` is the one signature element and the only place dark-on-light
inversion is allowed. Four layers, all in CSS, nothing fetched. Bottom to top:

```
background   ramp    ← linear-gradient(100deg, depth-1 → depth-2 26% → depth-3 62% → depth-4)
             bloom   ← radial light-leak of depth-1, 55%×75% at 88% 30%, 46% at its peak
             engrave ← two sets of hairline concentric rings (9px and 13px pitch, ~3% white)
::before     veil    ← linear-gradient(100deg) of #080c2e at 76% → 72% → 55% → 54%
::after      grain   ← inline SVG feTurbulence, opacity .18, mix-blend-mode overlay
content      text lives here, above every layer
```

The engraving is the guilloché on a banknote: where the two ring sets cross, the
interference is the pattern. It is deliberately the only decorative idea on the
surface, and its pitch is wide because at 5px and 7px the sets beat against each
other and against the pixel grid into something that reads as noise.

The **veil is the accessibility device**, not a mood adjustment. Its alpha tracks
the ramp position rather than the box, so it is thickest where the cyan is and
lifts toward the dark end. That floor is what makes the whole surface safe to
write on:

| Surface | White text | `on-dark-muted` | Where the worst pixel is |
| --- | --- | --- | --- |
| `.hero-gradient` (the band, the auth page) | 7.71 | 5.19 | its brightest point, top-left |
| `.hero-card` (the account card face) | 11.18 | 7.53 | over the bloom, right of centre |

`.hero-card` is the same surface retuned for a face that carries copy edge to
edge: the ramp reaches its dark stop sooner (0% → 34% → 70% → 100%) and the veil
never lifts above 70%. Both surfaces also carry one hairline of light along the
top edge — `inset 0 1px 0` at 16% white — which is the detail that separates a
plate from a flat fill.

Every number above is composited, not sampled from a swatch: it includes the
bloom at its peak, the grain's lightest speckle, and both engraving sets landing
on the same pixel. Because the bloom is sized in percentages, its peak alpha does
not move when the box is resized, so a layout change cannot quietly break a
ratio.

Rules:

- **Copy may land anywhere on either surface.** The cyan end is still the band
  you *shouldn't* write on — the veil makes it survivable, the design still
  prefers the dark two-thirds.
- The grain is an inline `data:` SVG — no image file, nothing to cache-bust — and
  it is **static**. There is no animated texture to opt out of.
- `z-index: -1 / -2` with `isolation: isolate` keeps both pseudo-elements under
  the content and inside the element, so a hero never paints over its neighbours.
- Dimming is available as a device: white at 55% alpha over its own local
  background is still 5.16:1 on the card face, against 12.23:1 for the full-white
  digits beside it. That is how the masked groups on `GradientAccountCard` are
  set — quieter than the number, never unreadable.

---

## 3. Typography

Two self-hosted roles, both imported in
[`main.tsx`](../frontend/src/main.tsx) before `index.css`:

- **Manrope Variable** (`@fontsource-variable/manrope`) carries the prose and the
  display headlines. The build emits five subset files (latin, latin-ext, greek,
  cyrillic, vietnamese), each on the 200–800 variable axis with
  `font-display: swap`. The peso sign is in the latin-ext subset, so `₱` renders
  from Manrope rather than falling back mid-string.
- **IBM Plex Mono** (`@fontsource/ibm-plex-mono`, weights 400/500/600) carries
  the data. A reference, a timestamp and a column of amounts are read digit by
  digit, and only a fixed advance keeps such a column from rippling. It is a
  runtime dependency, not a dev one: the font ships with the app.

`--font-mono` is the token; `ui.ts` exports `figure = 'font-mono tabular-nums'`
so a data cell is one class pair, and `accountNumber` adds the tracking a
hand-typed number needs.

### Display scale

Each size carries its own leading and tracking, so a headline cannot be assembled
without the tight, heavy setting:

| Token | Size | Line height | Tracking |
| --- | --- | --- | --- |
| `text-display-sm` | 1.25rem | 1.1 | −0.015em |
| `text-display-md` | 1.75rem | 1.05 | −0.02em |
| `text-display-lg` | 2.5rem | 1 | −0.025em |
| `text-display-xl` | `clamp(2.75rem, 1.5rem + 5.5vw, 4.5rem)` | 0.94 | −0.03em |

`.display` (the `display` export in `ui.ts`) adds `text-transform: uppercase`,
`font-weight: 800` and `text-wrap: balance`. **Uppercase is for display
headlines and eyebrows only** — never for a sentence of body copy, and never for
a number a person has to read digit by digit.

Body copy uses Tailwind's stock sizes (`text-sm`, `text-base`) with the semantic
text colours. `ui.ts` exports `heading`, `subheading`, `eyebrow` and
`eyebrowOnDark` so a section header is assembled, not improvised.

### Numerals

**Every amount is tabular.** `ui.ts` exports `money = 'tabular-nums'`; account
numbers add `accountNumber` for the extra tracking. Verified in the browser: ten
`1`s and ten `0`s measure identically (198.41px at 32px) with the utility and
differ by 70px without it.

A money figure at display size uses `moneyDisplay` instead, and the reason is a
glyph: Manrope's `₱` has no right side bearing — its ink runs the full width of
its advance — so at headline sizes the sign touches the first digit and the two
read as one character. `moneyDisplay` is `tabular-nums tracking-[0.05em]`; that
much space between digits is wrong in a table, which is why it is a separate
token and only the page's big numbers use it.

`.tabular-money` (unlayered, in `index.css`) additionally forces `text-align:
right` for table columns. It is unlayered on purpose so it beats a stray
`text-left`. New components should use Tailwind's own `tabular-nums` and set
their own alignment.

Amounts are **strings end to end** — the backend sends `"7000.00"`, the forms
submit strings, and `frontend/src/lib/money.ts` does the arithmetic on digits.
Nothing in this system formats a number: `formatPesos` regroups a string.

---

## 4. Space, radius, elevation, motion

- **Spacing** is Tailwind's 4px scale. Use `gap-*` and `space-y-*` over margins
  on children.
- **Radius**: `rounded-card` (20px) for every card and hero surface,
  `rounded-inset` (14px) for anything set into one, `rounded-lg` for fields and
  sheets, `rounded-full` for every button and pill. Buttons are pills in both
  directions — that is what distinguishes an action from a surface at a glance.
- **Elevation** is three steps, tinted with the ink ramp rather than black so a
  lifted card reads as glass and not as a grey smudge:
  `shadow-elevate-1` is a card at rest, `-2` a card a control sits on or one that
  overlaps the hero, `-3` a sheet over the page. The `card` and `cardRaised`
  exports in `ui.ts` already pick the right step.
- **Tap targets** are ≥44px (`min-h-11`) on every button and input, at every
  breakpoint. `min-h-9` is allowed only for `size="sm"`, which is for text
  actions inside a card header, not for a primary control.
- **Motion** is 150–300ms, `transform` and `opacity` only. `.press` gives the
  press-down scale; `sheet-backdrop`, `sheet-panel` and `toast-panel` have
  entrance keyframes; `skeleton-sweep` is the loading shimmer.
- `prefers-reduced-motion: reduce` collapses all of it to 0.01ms and hides the
  skeleton sweep. Nothing depends on animation to communicate state — the button
  label changes to the `-ing` form, the Alert carries an icon, the toast says what
  happened.

---

## 5. Components

Everything in [`frontend/src/components/`](../frontend/src/components/). Each one
is tested; the behaviours worth knowing are in its test file.

| Component | Reach for it when | Don't |
| --- | --- | --- |
| `Button` | Any action. Six variants (`primary`, `secondary`, `danger`, `ghost`, `onDark`, `onDarkOutline`), two sizes, `loading` | Hand-write the class string. Use `buttonClasses(variant, size)` on a `<Link>` instead — never a `<div onClick>` |
| `Input` | A labelled text field with hint + error wiring. `tone="onDark"` for a form laid on the gradient (tinted field, half-white outline, light-red error); `suffix` for a control inside the right edge | A bare `<input>`; the generated `id` and `aria-describedby` are the point. A white field on the gradient — the white pill below it then reads as a third field |
| `PasswordInput` | Any password. Show / Hide toggle, back to hidden on submit | An `aria-label` on the toggle: its name comes from its text ("Show password"), so `getByLabel('Password', { exact: true })` still finds only the field |
| `MoneyInput` | Any amount. ₱ prefix, live formatted preview, `type="text"` + `inputMode="decimal"` | `type="number"` — it returns a JS number in some browsers and accepts `1e5` |
| `Card` | A titled surface with optional actions | An untitled box; put `card` on the element you already have |
| `GradientAccountCard` | An account as an object: masked number, copy button, balance | Showing a full account number on screen — the copy button is the only way it leaves, and the reveal is the clipboard-denied fallback |
| `SegmentedPanels` | One panel with several mutually exclusive views: the money forms on the account page | Three tall cards of equal height holding one short form each. It is the ARIA tabs pattern, so the segments are named by what they show |
| `Sheet` | A modal layer. `center` for a decision, `bottom` for a choice | `<dialog>` — jsdom has no `showModal()`, so the behaviour would be untested. `ConfirmDialog` is the money-decision wrapper |
| `Toast` | A one-line result over the page | Auto-dismissing an error: `duration` defaults to 0 for `tone="error"` because the message carries the fix |
| `Alert` | A message that belongs to the page, not the viewport | `Toast` for something the user must act on |
| `Skeleton` | Holding the shape of loading content | Showing it alone — pair it with a visible "Loading" message |
| `EmptyState` / `EmptyLine` | A screen or a list slot with nothing in it | A vague "No data"; say what is missing and offer the control that fixes it |
| `Icon` | Any glyph. Twelve stroked icons on a 24 grid | An emoji, or a second icon set |
| `Logo` / `LogoWordmark` | The Sinag mark — a sun rising over a horizon, five rounded rays; a path, so it recolours with the text | An image file; a separate raster logo for headers |

### Accessibility rules already baked in

Do not re-solve these per page:

- Focus rings are **outline**, not `ring`, so they survive an `overflow` clip and
  stay visible against a white card edge. `focusRing` on light, `focusRingOnDark`
  on the gradient — a blue ring on a red toast is not an indicator.
- Errors get `aria-invalid`, `aria-describedby`, and `role="alert"` **only when
  invalid**, so neutral guidance never interrupts.
- Colour is never the only signal: Alerts and Toasts carry an icon, the account
  card says "Copied" in words, and the transaction table signs inflows and
  outflows with text as well as colour.
- `Sheet` traps Tab, closes on Escape and backdrop press, locks page scroll,
  refuses all of it while `busy`, and returns focus to the trigger.
- The modal's first stop is **Cancel**, not Confirm — a stray Enter presses must
  not move money.
- `SegmentedPanels` is the ARIA tabs pattern, including the part a screenshot
  cannot show: only the selected segment is in the tab order, and Arrow / Home /
  End move the selection and the focus together.
- A control's accessible name is the verb on it. The money rail's pills carry
  `aria-label` of "Deposit" / "Withdraw" / "Transfer" with the icon
  `aria-hidden`, so nothing announces as "DepositInto savings".

### One Tailwind trap worth knowing

Two utilities that set the *same property* are ordered by Tailwind's theme sort,
not by the order you wrote them. So `cx('ring-field-border', 'ring-danger')` does
**not** reliably give a red border — it silently keeps the grey. Anything with a
state-dependent colour must **choose** one class (`invalid ? inputError :
inputIdle`), which is how `Input` handles its border, fill and hint. The guard tests
are in [`Input.test.tsx`](../frontend/src/components/Input.test.tsx).

Its cousin: never pair `focus:outline-none` with `focus-visible:outline-2`. In v4
`outline-none` sets the outline-style variable the focus-visible ring then
inherits, so the ring computes to `none` — every field lost its keyboard ring
that way once. Browsers already limit the default outline to `:focus-visible`,
so there is nothing to suppress.

---

## 6. The pages today

Every screen is laid out on the primitives rather than beside them. What each one
owes to the system:

- **Auth** (`AuthLayout`) is the full-height `.hero-gradient`: brand panel and
  form panel from `lg` up, only the form below it. The form sits on the gradient
  rather than in a sheet, so `Input tone="onDark"` pairs with a white
  `Button variant="onDark"`.
- **Customer dashboard** is one hero band — eyebrow, headline, total, and the
  three money verbs as `railAction` pills on the bright end — over a grid of
  `GradientAccountCard`s. The band is a band, not a screen: it is `rounded-card`
  inside the page container, so it has the same edges as everything under it.
- **Account page** is a hero band, then the statement pulled up over it
  (`-mt-12`, `cardRaised`), then one `Move money` card whose three forms live in
  a `SegmentedPanels`. A form column is capped at `max-w-2xl`: a money field that
  stretches to a 1440px line is a field people misread.
- **Admin overview and review queue** use the same band and the same cards. They
  inherit the tokens rather than repeating them, and the grid that holds the two
  summary cards is `items-start` so a short card is not stretched into a void by
  a tall neighbour.
- **Transactions** read as a statement: a day header with that day's in and out
  totals, mono references, and a balance-after column.

The rule for the next page: take the layout from the nearest of the above, and if
a value you need is not a token, add the token to `index.css` rather than typing
the value.

---

## 7. Verifying

```bash
cd frontend
npm run typecheck     # tsc -b
npm run lint          # eslint . — one pre-existing warning, no errors
npm test              # vitest
npm run build
```

Contrast is checked with the WCAG 2.2 relative-luminance formula against the
composited background (ramp stop → bloom → engraving → veil alpha → grain
blend → any panel colour painted over it), not against a swatch. The ratios in
this document came from a throwaway script doing that arithmetic; re-run it
whenever a token or the veil floor changes, and re-check the two surfaces
separately — `.hero-card` and the auth form column have different floors because
they paint different layers over the same ramp.

Three things can only be checked in a browser, and all three have been, against
screenshots at 1440px and 390px:

- **Tabular figures** — measure ten `1`s against ten `0`s on an element with
  `tabular-nums`; equal widths mean the feature is actually applied by the font.
- **Focus visibility** — Tab to a control and read `outline-style`, `outline-width`
  and `outline-color` from the computed style. Programmatic `.focus()` does not
  trigger `:focus-visible`, so it must be a real key press.
- **The engraving at rest** — the ring sets are a real moiré risk against a
  device pixel grid. If a screenshot shows shimmer rather than line work, widen
  the pitch before lowering the alpha; a faint dense pattern is still a dense
  pattern.

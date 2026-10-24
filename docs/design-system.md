# CIICC Banking — design system

The visual rules for the React frontend: what colours exist, what they mean, how
type is set, and which component to reach for. Two files implement it:

| File | Role |
| --- | --- |
| [`frontend/src/index.css`](../frontend/src/index.css) | The theme. Tailwind v4 is configured in CSS, so every token below is a real utility (`bg-action`, `text-display-md`). |
| [`frontend/src/components/ui.ts`](../frontend/src/components/ui.ts) | Shared class strings assembled from those tokens, plus the component primitives that use them. |

There is no `tailwind.config.*` file. Do not add one.

**Scope.** This pass built the tokens and the primitives. The pages still render
the older layouts on top of them — they pick up the palette and type because
they consume the same class strings, but they have not been re-laid-out yet.
See [Migrating the pages](#migrating-the-pages).

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
| `--color-depth-scrim` | `#030814` |

**Never paint a depth stop and write on it.** Bare, white text is 1.53:1 on
`depth-1` and 3.73:1 on `depth-2` — both fail. Use `.hero-gradient`, which adds
the scrim described below.

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
relative-luminance formula, not estimated — see [Verifying](#verifying).

`danger-on-dark` `#fecaca` is error text on the gradient, where `danger` is about
2:1. Measured against the brightest rendered pixel beside the auth form fields
(`rgb(17,40,79)`): error text 10.08, muted hint 9.82, white label 14.58, and the
half-white field outline 4.75 (non-text, needs 3:1).

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
inversion is allowed. Three layers, all in CSS, nothing fetched:

```
content            ← text lives here, above both pseudo-elements
::after  grain     ← inline SVG feTurbulence, opacity .18, mix-blend-mode overlay
::before scrim     ← vertical gradient, 66% → 72% → 90% of #030814
background         ← linear-gradient(155deg, depth-1 → depth-2 22% → depth-3 56% → depth-4 88%)
```

The **scrim is the accessibility device**, not a mood adjustment. Its 66% floor
is what makes the whole surface safe to write on:

| Position on the gradient | White text | `on-dark-muted` | With the grain's lightest speckle: white / muted |
| --- | --- | --- | --- |
| top (`depth-1` cyan) | 8.62 | 5.80 | 7.66 / 5.16 |
| 22% (`depth-2`) | 12.94 | 8.71 | 11.98 / 8.07 |
| 56% (`depth-3`) | 18.77 | 12.64 | 18.45 / 12.43 |
| bottom (`depth-4`) | 20.02 | 13.49 | 19.91 / 13.41 |

So a layout change cannot push a headline into an inaccessible band — the worst
case anywhere on the surface is 5.16, and that is small secondary text under a
pessimistic grain assumption.

Rules:

- **Text goes on the dark half.** The scrim guarantees it is safe anywhere, but
  the design intent is white copy landing on indigo-to-black, with cyan reserved
  for the band you *don't* write on.
- The grain is an inline `data:` SVG — no image file, nothing to cache-bust — and
  it is **static**. There is no animated texture to opt out of.
- `z-index: -1 / -2` with `isolation: isolate` keeps both layers under the
  content and inside the element, so a hero never paints over its neighbours.
- `.hero-plate` (`#030814` at 72%) is available for a solid panel that should
  read as part of the surface rather than a card dropped on top of it.

---

## 3. Typography

One self-hosted family: **Manrope Variable**, from `@fontsource-variable/manrope`,
imported in [`main.tsx`](../frontend/src/main.tsx) before `index.css`. The build
emits five subset files (latin, latin-ext, greek, cyrillic, vietnamese), each on
the 200–800 variable axis with `font-display: swap`. The peso sign is in the
latin-ext subset, so `₱` renders from Manrope rather than falling back mid-string.

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

`.tabular-money` (unlayered, in `index.css`) additionally forces `text-align:
right` for table columns. It is unlayered on purpose so it beats a stray
`text-left`. New components should use Tailwind's own `tabular-nums` and set
their own alignment.

Amounts are **strings end to end** — the backend sends `"7000.00"`, the forms
submit strings, and `frontend/src/lib/money.ts` does the arithmetic on digits.
Nothing in this system formats a number: `formatPesos` regroups a string.

---

## 4. Space, radius, motion

- **Spacing** is Tailwind's 4px scale. Use `gap-*` and `space-y-*` over margins
  on children.
- **Radius**: `rounded-lg` for fields and sheets, `rounded-xl` for cards,
  `rounded-2xl` for the account card, `rounded-full` for every button. Buttons
  are pills in both directions — that is what distinguishes an action from a
  surface at a glance.
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
| `ActionTile` | A tappable square on the money-actions row | A tile whose accessible name is the glyph; the icon is `aria-hidden` and `aria-label` carries the verb |
| `Sheet` | A modal layer. `center` for a decision, `bottom` for a choice | `<dialog>` — jsdom has no `showModal()`, so the behaviour would be untested. `ConfirmDialog` is the money-decision wrapper |
| `Toast` | A one-line result over the page | Auto-dismissing an error: `duration` defaults to 0 for `tone="error"` because the message carries the fix |
| `Alert` | A message that belongs to the page, not the viewport | `Toast` for something the user must act on |
| `Skeleton` | Holding the shape of loading content | Showing it alone — pair it with a visible "Loading" message |
| `EmptyState` / `EmptyLine` | A screen or a list slot with nothing in it | A vague "No data"; say what is missing and offer the control that fixes it |
| `Icon` | Any glyph. Twelve stroked icons on a 24 grid | An emoji, or a second icon set |
| `Logo` / `LogoWordmark` | The CIICC mark — an open ring with a transfer arrow leaving through the gap | An image file; it is a path, so it recolours with the text |

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

## 6. Migrating the pages

The primitives are live and tested. When re-laying out a page:

1. Start with the hero. `AuthLayout` is the auth pattern — full-height
   `.hero-gradient`, a brand panel and dark form panel on desktop, and only the
   form on smaller screens. Its form sits on the gradient rather than in a
   sheet, so pair `Input tone="onDark"` with a white `Button variant="onDark"`.
2. Swap the account list for `GradientAccountCard` and the action row for
   `ActionTile tone="onDark"`.
3. Wrap card content in `Card` rather than repeating `cx(card, …)`.
4. Replace inline `<button className={buttonPrimary}>` with `<Button>` so
   `loading` is one prop instead of a conditional spinner at the call site.
5. Keep the data fetching, forms, routes and validation exactly as they are. This
   is a visual pass; every existing test must still pass unchanged.

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
composited background (gradient stop → scrim alpha → grain blend), not against a
swatch. The ratios in this document came from a throwaway script doing that
arithmetic; re-run it whenever a token or the scrim floor changes.

Two things can only be checked in a browser, and both have been:

- **Tabular figures** — measure ten `1`s against ten `0`s on an element with
  `tabular-nums`; equal widths mean the feature is actually applied by the font.
- **Focus visibility** — Tab to a control and read `outline-style`, `outline-width`
  and `outline-color` from the computed style. Programmatic `.focus()` does not
  trigger `:focus-visible`, so it must be a real key press.

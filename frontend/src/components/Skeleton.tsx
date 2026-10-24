import { cx } from './ui'

/**
 * Placeholder for content that has not arrived. The shape is what matters — a
 * block the width of the thing coming keeps the page from jumping when the data
 * lands, which a centred spinner cannot do.
 *
 * Always pair a group of these with a visible "Loading…" message: the bars are
 * `aria-hidden`, so a screen reader needs the words to know the page is working
 * rather than empty.
 */
export function Skeleton({ className }: { className?: string }) {
  return <span aria-hidden="true" className={cx('skeleton block rounded-md', className)} />
}

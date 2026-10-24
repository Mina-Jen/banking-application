import { useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { cx, focusRing, segmentActive, segmentIdle, segmented } from './ui'

export interface Segment {
  id: string
  /** The verb or noun a person chooses, not a description of the panel. */
  label: string
  content: ReactNode
}

/**
 * One panel, several mutually exclusive views: the pill row chooses which is
 * shown, so three short forms sit in the space of one instead of in three tall
 * boxes with their content at the top.
 *
 * Follows the ARIA tabs pattern, including the part a screenshot cannot show:
 * only the selected segment is in the tab order, and the arrow keys move the
 * selection, so the control behaves the same from a keyboard as from a pointer.
 */
export function SegmentedPanels({ segments, label }: { segments: Segment[]; label: string }) {
  const baseId = useId()
  const [active, setActive] = useState(segments[0]?.id ?? '')
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({})

  const index = Math.max(
    0,
    segments.findIndex((segment) => segment.id === active),
  )

  function select(id: string) {
    setActive(id)
    // Arrow keys move the selection and the focus together, so the pill that
    // changes is the pill under the caret.
    tabRefs.current[id]?.focus()
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const targets: Record<string, number> = {
      ArrowRight: (index + 1) % segments.length,
      ArrowLeft: (index - 1 + segments.length) % segments.length,
      Home: 0,
      End: segments.length - 1,
    }
    const next = targets[event.key]
    if (next === undefined) {
      return
    }
    event.preventDefault()
    select(segments[next].id)
  }

  const current = segments.find((segment) => segment.id === active) ?? segments[0]

  return (
    <div>
      <div
        role="tablist"
        aria-label={label}
        onKeyDown={onKeyDown}
        className={cx(segmented, 'flex-wrap')}
      >
        {segments.map((segment) => {
          const selected = segment.id === active
          return (
            <button
              key={segment.id}
              ref={(node) => {
                tabRefs.current[segment.id] = node
              }}
              id={`${baseId}-tab-${segment.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${baseId}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => setActive(segment.id)}
              className={cx(
                'press min-h-11 rounded-full px-4 text-sm font-semibold',
                focusRing,
                selected ? segmentActive : segmentIdle,
              )}
            >
              {segment.label}
            </button>
          )
        })}
      </div>

      <div
        id={`${baseId}-panel`}
        role="tabpanel"
        aria-labelledby={`${baseId}-tab-${active}`}
        tabIndex={0}
        className={cx('mt-5 rounded-inset', focusRing)}
      >
        {current?.content}
      </div>
    </div>
  )
}

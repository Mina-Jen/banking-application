import type { SVGProps } from 'react'

/**
 * The interface icons: one family, 24px grid, 1.75 stroke, round joins. Emoji
 * are never icons — they cannot be themed, sized or recoloured, and they render
 * differently on every operating system.
 *
 * Pass `title` when the icon carries meaning on its own. Without it the icon is
 * hidden from assistive technology, which is correct when a visible label or an
 * `aria-label` already says what the control does.
 */
type IconProps = Omit<SVGProps<SVGSVGElement>, 'children'> & {
  title?: string
  children?: SVGProps<SVGSVGElement>['children']
}

function Icon({ title, children, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      {...rest}
    >
      {title ? <title>{title}</title> : null}
      {children}
    </svg>
  )
}

export function IconDeposit(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 5v14M5 12h14" />
    </Icon>
  )
}

export function IconWithdraw(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M5 12h14" />
      <path d="M12 19V5" />
      <path d="M7 14l5 5 5-5" />
    </Icon>
  )
}

export function IconTransfer(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4 8h13M13 4l4 4-4 4" />
      <path d="M20 16H7M11 12l-4 4 4 4" />
    </Icon>
  )
}

export function IconArrowDownLeft(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M18 6 6 18M6 10v8h8" />
    </Icon>
  )
}

export function IconArrowUpRight(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 18 18 6M10 6h8v8" />
    </Icon>
  )
}

export function IconCard(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
      <path d="M2.5 10h19" />
      <path d="M6 15h4" />
    </Icon>
  )
}

export function IconCopy(props: IconProps) {
  return (
    <Icon {...props}>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M15 5.5A1.5 1.5 0 0 0 13.5 4H5.5A1.5 1.5 0 0 0 4 5.5v8A1.5 1.5 0 0 0 5.5 15" />
    </Icon>
  )
}

export function IconCheck(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M4.5 12.5l5 5 10-11" />
    </Icon>
  )
}

export function IconClose(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Icon>
  )
}

export function IconAlert(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M12 4.5 2.8 20h18.4L12 4.5Z" />
      <path d="M12 10v4.5" />
      <path d="M12 17.4h.01" />
    </Icon>
  )
}

export function IconInfo(props: IconProps) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5.5M12 7.8h.01" />
    </Icon>
  )
}

export function IconInbox(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M3.5 13.5 6 5h12l2.5 8.5V19H3.5v-5.5Z" />
      <path d="M3.5 13.5H8l1.5 2.5h5L16 13.5h4.5" />
    </Icon>
  )
}

export function IconArrowLeft(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M19 12H5M11 6l-6 6 6 6" />
    </Icon>
  )
}

export function IconRefresh(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="M20 11a8 8 0 1 0-2.3 6.2" />
      <path d="M20 5.5V11h-5.5" />
    </Icon>
  )
}

export function IconChevronRight(props: IconProps) {
  return (
    <Icon {...props}>
      <path d="m9 6 6 6-6 6" />
    </Icon>
  )
}

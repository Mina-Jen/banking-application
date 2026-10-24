/**
 * Timestamps from the API are ISO-8601 strings. The admin screens show the
 * exact moment a check or a flag happened, so they need date AND time, unlike
 * the account history which groups by day.
 */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return 'time unavailable'
  }
  return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

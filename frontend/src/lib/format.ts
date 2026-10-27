const won = new Intl.NumberFormat('ko-KR')

export function formatWon(value: number | null | undefined): string {
  return value == null ? '-' : `${won.format(value)}원`
}

export function formatNumber(value: number | null | undefined): string {
  return value == null ? '-' : won.format(value)
}

/** 0.025 → 2.50% */
export function formatRate(rate: number | null | undefined): string {
  return rate == null ? '-' : `${(rate * 100).toFixed(2)}%`
}

/** 2026-10-27T14:03:00.123 → 2026-10-27 14:03 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  return value.replace('T', ' ').substring(0, 16)
}

export function formatDate(value: string | null | undefined): string {
  return value ? value.substring(0, 10) : '-'
}

/** yyyy-MM-dd 문자열에 일 수를 더한다. 시간대 영향을 받지 않도록 UTC 기준으로 계산 */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().substring(0, 10)
}

/** 사용자 PC 기준 오늘 날짜 (yyyy-MM-dd) */
export function today(): string {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().substring(0, 10)
}

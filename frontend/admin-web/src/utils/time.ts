export function formatUtcTime(value?: string | null): string {
  if (!value) return '-'
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
  const date = new Date(hasZone ? value : `${value}Z`)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 16)
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

const TONES: Record<string, string> = {
  REQUESTED: 'info',
  APPROVED: 'progress',
  SHIPPED: 'done',
  REJECTED: 'danger',
  CANCELED: 'muted',
  ON_SALE: 'done',
  STOPPED: 'muted',
  ACTIVE: 'done',
  INACTIVE: 'muted',
  CURRENT: 'done',
  SCHEDULED: 'info',
  ENDED: 'muted',
  NONE: 'muted',
  OPEN: 'info',
  CLOSED: 'done',
}

interface Props {
  value: string
  label: string
}

export default function StatusBadge({ value, label }: Props) {
  return <span className={`badge badge-${TONES[value] ?? 'muted'}`}>{label}</span>
}

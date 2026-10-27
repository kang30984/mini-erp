import { ORDER_EVENTS, labelOf } from '../lib/codes'
import { formatDateTime } from '../lib/format'
import type { OrderHistory } from '../types/order'

export default function OrderHistoryList({ histories }: { histories: OrderHistory[] }) {
  return (
    <ol className="history">
      {histories.map((h, index) => (
        <li key={index}>
          <span className="history-status">{labelOf(ORDER_EVENTS, h.toStatus)}</span>
          <span className="history-meta">
            {formatDateTime(h.changedAt)} {h.changedByName}
          </span>
          {h.reason && <span className="history-reason">{h.reason}</span>}
        </li>
      ))}
    </ol>
  )
}

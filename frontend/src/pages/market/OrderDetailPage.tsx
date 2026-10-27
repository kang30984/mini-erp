import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { useCancelOrder, useMyOrder } from '../../api/market'
import DataTable, { type Column } from '../../components/DataTable'
import Modal from '../../components/Modal'
import StatusBadge from '../../components/StatusBadge'
import { ORDER_EVENTS, ORDER_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime, formatNumber, formatWon } from '../../lib/format'
import type { OrderItem } from '../../types/order'

const itemColumns: Column<OrderItem>[] = [
  { key: 'line', header: '번호', render: (i) => i.lineNo, align: 'center', width: '56px' },
  {
    key: 'product',
      wrap: true,
    header: '상품',
    render: (i) => (
      <div className="cell-stack">
        <span>{i.productName}</span>
        <span className="code">{i.sku}</span>
      </div>
    ),
  },
  { key: 'price', header: '단가', render: (i) => formatWon(i.unitPrice), align: 'right', width: '130px' },
  { key: 'qty', header: '수량', render: (i) => formatNumber(i.qty), align: 'right', width: '72px' },
  { key: 'amount', header: '금액', render: (i) => formatWon(i.amount), align: 'right', width: '140px' },
]

export default function OrderDetailPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const created = (location.state as { created?: boolean } | null)?.created
  const { data: order, isPending, isError, error } = useMyOrder(id)
  const cancel = useCancelOrder()
  const [confirming, setConfirming] = useState(false)

  if (isPending) return <p className="page-status">주문을 불러오는 중입니다.</p>
  if (isError) {
    return (
      <div className="page-status">
        <p>{error.message}</p>
        <Link to="/market/orders">주문 내역으로 돌아가기</Link>
      </div>
    )
  }

  return (
    <section>
      <div className="page-header">
        <div>
          <Link to="/market/orders" className="back-link">주문 내역</Link>
          <h1 className="page-title code-title">{order.orderNo}</h1>
        </div>
        {order.status === 'REQUESTED' && (
          <button type="button" className="button button-secondary" onClick={() => setConfirming(true)}>
            주문 취소
          </button>
        )}
      </div>

      {created && order.status === 'REQUESTED' && (
        <p className="form-success" role="status">
          주문을 접수했습니다. 담당자가 승인하면 출고가 진행됩니다.
        </p>
      )}

      <dl className="detail-grid">
        <div>
          <dt>상태</dt>
          <dd><StatusBadge value={order.status} label={labelOf(ORDER_STATUSES, order.status)} /></dd>
        </div>
        <div>
          <dt>주문 금액</dt>
          <dd className="amount">{formatWon(order.totalAmount)}</dd>
        </div>
        <div>
          <dt>주문일시</dt>
          <dd>{formatDateTime(order.orderedAt)}</dd>
        </div>
        <div>
          <dt>승인일시</dt>
          <dd>{formatDateTime(order.approvedAt)}</dd>
        </div>
        <div>
          <dt>출고일시</dt>
          <dd>{formatDateTime(order.shippedAt)}</dd>
        </div>
      </dl>

      <h2 className="section-title">주문 상품</h2>
      <DataTable
        caption="주문 상품"
        columns={itemColumns}
        rows={order.items}
        rowKey={(i) => i.lineNo}
        emptyMessage="주문 상품이 없습니다."
      />

      <h2 className="section-title">처리 이력</h2>
      <ol className="history">
        {order.histories.map((h, index) => (
          <li key={index}>
            <span className="history-status">{labelOf(ORDER_EVENTS, h.toStatus)}</span>
            <span className="history-meta">
              {formatDateTime(h.changedAt)} {h.changedByName}
              {h.reason && ` (${h.reason})`}
            </span>
          </li>
        ))}
      </ol>

      <Modal open={confirming} title="주문 취소" onClose={() => setConfirming(false)}>
        <p>{order.orderNo} 주문을 취소합니다. 취소한 주문은 되돌릴 수 없습니다.</p>
        {cancel.isError && <p className="form-error" role="alert">{cancel.error.message}</p>}
        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={() => setConfirming(false)}>
            닫기
          </button>
          <button
            type="button"
            className="button button-danger"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate(order.id, { onSuccess: () => setConfirming(false) })}
          >
            주문 취소
          </button>
        </div>
      </Modal>
    </section>
  )
}

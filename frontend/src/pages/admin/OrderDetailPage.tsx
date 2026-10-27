import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAdminOrder, useOrderAction, useRegisterReturn } from '../../api/adminOrders'
import DataTable, { type Column } from '../../components/DataTable'
import Modal from '../../components/Modal'
import OrderHistoryList from '../../components/OrderHistoryList'
import OrderReturnsTable from '../../components/OrderReturnsTable'
import StatusBadge from '../../components/StatusBadge'
import { ORDER_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime, formatNumber, formatWon } from '../../lib/format'
import type { OrderAction, OrderDetail, OrderItem } from '../../types/order'

interface ActionConfig {
  action: OrderAction
  title: string
  description: string
  confirmLabel: string
  needsReason?: boolean
  danger?: boolean
}

const ACTIONS: Record<string, ActionConfig[]> = {
  REQUESTED: [
    {
      action: 'approve',
      title: '주문 승인',
      description: '승인하면 주문 수량만큼 재고가 바로 차감됩니다.',
      confirmLabel: '승인',
    },
    {
      action: 'reject',
      title: '주문 반려',
      description: '반려 사유는 거래처 주문 화면에 함께 표시됩니다.',
      confirmLabel: '반려',
      needsReason: true,
      danger: true,
    },
  ],
  APPROVED: [
    {
      action: 'ship',
      title: '출고 처리',
      description: '출고 시각이 정산 기준일이 됩니다. 출고 후에는 취소할 수 없고 반품으로만 처리할 수 있습니다.',
      confirmLabel: '출고 처리',
    },
    {
      action: 'cancel',
      title: '승인 취소',
      description: '주문을 취소하고 차감했던 재고를 되돌립니다.',
      confirmLabel: '주문 취소',
      danger: true,
    },
  ],
}

function ActionDialog({ order, config, onClose }: { order: OrderDetail; config: ActionConfig; onClose: () => void }) {
  const run = useOrderAction()
  const [reason, setReason] = useState('')
  const reasonMissing = config.needsReason && reason.trim() === ''

  return (
    <>
      <p>{config.description}</p>
      {(config.needsReason || config.action === 'cancel') && (
        <div className="field">
          <label htmlFor="reason">사유{config.needsReason ? '' : ' (선택)'}</label>
          <textarea id="reason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
      )}
      {run.isError && <p className="form-error" role="alert">{run.error.message}</p>}
      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={onClose}>
          닫기
        </button>
        <button
          type="button"
          className={`button ${config.danger ? 'button-danger' : 'button-primary'}`}
          disabled={run.isPending || reasonMissing}
          onClick={() =>
            run.mutate(
              { id: order.id, action: config.action, version: order.version, reason: reason.trim() || undefined },
              { onSuccess: onClose },
            )
          }
        >
          {config.confirmLabel}
        </button>
      </div>
    </>
  )
}

function ReturnDialog({ order, item, onClose }: { order: OrderDetail; item: OrderItem; onClose: () => void }) {
  const register = useRegisterReturn()
  const returnable = item.qty - item.returnedQty
  const [qty, setQty] = useState(1)
  const [reason, setReason] = useState('')
  const invalid = qty < 1 || qty > returnable || reason.trim() === ''

  return (
    <>
      <p>
        {item.productName} · 주문 {item.qty}개 중 {returnable}개까지 반품할 수 있습니다. 반품 금액은 오늘이 속한 달의
        정산에서 차감되고, 반품 수량만큼 재고가 늘어납니다.
      </p>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="returnQty">반품 수량</label>
          <input
            id="returnQty"
            type="number"
            min={1}
            max={returnable}
            value={qty}
            onChange={(e) => setQty(Number(e.target.value))}
          />
        </div>
        <div className="field">
          <span className="field-label">반품 금액</span>
          <p className="field-value">{formatWon(item.unitPrice * Math.max(0, qty))}</p>
        </div>
        <div className="field field-wide">
          <label htmlFor="returnReason">반품 사유</label>
          <textarea id="returnReason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
      </div>
      {register.isError && <p className="form-error" role="alert">{register.error.message}</p>}
      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={onClose}>
          닫기
        </button>
        <button
          type="button"
          className="button button-primary"
          disabled={register.isPending || invalid}
          onClick={() =>
            register.mutate(
              { id: order.id, lineNo: item.lineNo, qty, reason: reason.trim() },
              { onSuccess: onClose },
            )
          }
        >
          반품 등록
        </button>
      </div>
    </>
  )
}

export default function AdminOrderDetailPage() {
  const id = Number(useParams().id)
  const { data: order, isPending, isError, error } = useAdminOrder(id)
  const [action, setAction] = useState<ActionConfig | null>(null)
  const [returnItem, setReturnItem] = useState<OrderItem | null>(null)

  if (isPending) return <p className="page-status">주문을 불러오는 중입니다.</p>
  if (isError) {
    return (
      <div className="page-status">
        <p>{error.message}</p>
        <Link to="/admin/orders">주문 목록으로 돌아가기</Link>
      </div>
    )
  }

  const shipped = order.status === 'SHIPPED'
  const itemColumns: Column<OrderItem>[] = [
    { key: 'line', header: '번호', render: (i) => i.lineNo, align: 'center', width: '56px' },
    {
      key: 'product',
      header: '상품',
      render: (i) => (
        <div className="cell-stack">
          <span>{i.productName}</span>
          <span className="code">{i.sku}</span>
        </div>
      ),
      wrap: true,
    },
    { key: 'price', header: '단가', render: (i) => formatWon(i.unitPrice), align: 'right', width: '130px' },
    { key: 'qty', header: '수량', render: (i) => formatNumber(i.qty), align: 'right', width: '64px' },
    {
      key: 'returned',
      header: '반품',
      render: (i) => (i.returnedQty ? formatNumber(i.returnedQty) : '-'),
      align: 'right',
      width: '64px',
    },
    { key: 'amount', header: '금액', render: (i) => formatWon(i.amount), align: 'right', width: '140px' },
  ]
  if (shipped) {
    itemColumns.push({
      key: 'return',
      header: '',
      render: (i) => (
        <button
          type="button"
          className="button button-small"
          disabled={i.returnedQty >= i.qty}
          onClick={() => setReturnItem(i)}
        >
          반품
        </button>
      ),
      align: 'center',
      width: '72px',
    })
  }

  return (
    <section>
      <div className="page-header">
        <div>
          <Link to="/admin/orders" className="back-link">주문 관리</Link>
          <h1 className="page-title code-title">{order.orderNo}</h1>
        </div>
        <div className="header-actions">
          {(ACTIONS[order.status] ?? []).map((config) => (
            <button
              key={config.action}
              type="button"
              className={`button ${config.danger ? 'button-secondary' : 'button-primary'}`}
              onClick={() => setAction(config)}
            >
              {config.title}
            </button>
          ))}
        </div>
      </div>

      <dl className="detail-grid">
        <div>
          <dt>거래처</dt>
          <dd>{order.partnerName} <span className="code">({order.partnerGradeCode})</span></dd>
        </div>
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

      {order.returns.length > 0 && (
        <>
          <h2 className="section-title">반품 내역</h2>
          <OrderReturnsTable returns={order.returns} />
        </>
      )}

      <h2 className="section-title">처리 이력</h2>
      <OrderHistoryList histories={order.histories} />

      <Modal open={action !== null} title={action?.title ?? ''} onClose={() => setAction(null)}>
        {action && <ActionDialog order={order} config={action} onClose={() => setAction(null)} />}
      </Modal>
      <Modal open={returnItem !== null} title="반품 등록" onClose={() => setReturnItem(null)}>
        {returnItem && <ReturnDialog order={order} item={returnItem} onClose={() => setReturnItem(null)} />}
      </Modal>
    </section>
  )
}

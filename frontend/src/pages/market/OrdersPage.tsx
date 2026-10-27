import { Link } from 'react-router'
import { useMyOrders } from '../../api/market'
import DataTable, { type Column } from '../../components/DataTable'
import Pagination from '../../components/Pagination'
import StatusBadge from '../../components/StatusBadge'
import { useListParams } from '../../hooks/useListParams'
import { ORDER_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime, formatWon } from '../../lib/format'
import type { OrderSummary } from '../../types/order'

const KEYS = ['status'] as const
const TABS = [{ value: '', label: '전체' }, ...ORDER_STATUSES]

function itemsLabel(o: OrderSummary) {
  return o.itemCount > 1 ? `${o.firstProductName} 외 ${o.itemCount - 1}건` : o.firstProductName
}

export default function OrdersPage() {
  const { values, page, apply, setPage } = useListParams(KEYS)
  const { data, isPending, isError, error } = useMyOrders({ ...values, page })

  const columns: Column<OrderSummary>[] = [
    {
      key: 'orderNo',
      header: '주문번호',
      render: (o) => <Link to={`/market/orders/${o.id}`} className="code">{o.orderNo}</Link>,
      width: '170px',
    },
    { key: 'items', wrap: true, header: '상품', render: itemsLabel },
    { key: 'amount', header: '주문 금액', render: (o) => formatWon(o.totalAmount), align: 'right', width: '140px' },
    {
      key: 'status',
      header: '상태',
      render: (o) => <StatusBadge value={o.status} label={labelOf(ORDER_STATUSES, o.status)} />,
      width: '100px',
    },
    { key: 'orderedAt', header: '주문일시', render: (o) => formatDateTime(o.orderedAt), width: '140px' },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">주문 내역</h1>
      </div>

      <div className="tabs" role="group" aria-label="주문 상태">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            aria-pressed={values.status === t.value}
            onClick={() => apply({ status: t.value })}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isError && <p className="form-error" role="alert">{error.message}</p>}

      <DataTable
        caption="주문 내역"
        columns={columns}
        rows={data?.items}
        rowKey={(o) => o.id}
        loading={isPending}
        emptyMessage={
          values.status ? '이 상태의 주문이 없습니다.' : <>아직 주문이 없습니다. <Link to="/market/products">상품 주문</Link>에서 시작해 보세요.</>
        }
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalCount={data.totalCount} onChange={setPage} />
      )}
    </section>
  )
}

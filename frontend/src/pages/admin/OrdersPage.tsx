import { Link } from 'react-router'
import { useAdminOrders, useOrderStatusCounts } from '../../api/adminOrders'
import DataTable, { type Column } from '../../components/DataTable'
import Pagination from '../../components/Pagination'
import StatusBadge from '../../components/StatusBadge'
import { useListParams } from '../../hooks/useListParams'
import { ORDER_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime, formatWon } from '../../lib/format'
import type { AdminOrderSummary } from '../../types/order'

const KEYS = ['status', 'keyword', 'from', 'to'] as const

function itemsLabel(o: AdminOrderSummary) {
  return o.itemCount > 1 ? `${o.firstProductName} 외 ${o.itemCount - 1}건` : o.firstProductName
}

export default function AdminOrdersPage() {
  const { values, page, apply, setPage, queryKey } = useListParams(KEYS)
  const { data, isPending, isError, error } = useAdminOrders({ ...values, page })
  const { data: counts } = useOrderStatusCounts()

  const columns: Column<AdminOrderSummary>[] = [
    {
      key: 'orderNo',
      header: '주문번호',
      render: (o) => <Link to={`/admin/orders/${o.id}`} className="code">{o.orderNo}</Link>,
      width: '170px',
    },
    { key: 'partner', header: '거래처', render: (o) => o.partnerName, width: '140px' },
    { key: 'items', header: '상품', render: itemsLabel, wrap: true },
    { key: 'amount', header: '주문 금액', render: (o) => formatWon(o.totalAmount), align: 'right', width: '140px' },
    {
      key: 'status',
      header: '상태',
      render: (o) => <StatusBadge value={o.status} label={labelOf(ORDER_STATUSES, o.status)} />,
      width: '100px',
    },
    { key: 'orderedAt', header: '주문일시', render: (o) => formatDateTime(o.orderedAt), width: '140px' },
  ]

  const tabs = [{ value: '', label: '전체' }, ...ORDER_STATUSES]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">주문 관리</h1>
      </div>

      <div className="tabs" role="group" aria-label="주문 상태">
        {tabs.map((t) => (
          <button
            key={t.value}
            type="button"
            aria-pressed={values.status === t.value}
            onClick={() => apply({ status: t.value })}
          >
            {t.label}
            {t.value && counts?.[t.value] ? <span className="tab-count">{counts[t.value]}</span> : null}
          </button>
        ))}
      </div>

      <form
        key={queryKey}
        className="search-bar"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          apply({
            keyword: String(f.get('keyword') ?? ''),
            from: String(f.get('from') ?? ''),
            to: String(f.get('to') ?? ''),
          })
        }}
      >
        <input name="keyword" defaultValue={values.keyword} placeholder="주문번호 또는 거래처명" aria-label="검색어" />
        <input type="date" name="from" defaultValue={values.from} aria-label="주문일 시작" />
        <span className="search-sep" aria-hidden="true">~</span>
        <input type="date" name="to" defaultValue={values.to} aria-label="주문일 끝" />
        <button type="submit" className="button button-secondary">검색</button>
      </form>

      {isError && <p className="form-error" role="alert">{error.message}</p>}

      <DataTable
        caption="주문 목록"
        columns={columns}
        rows={data?.items}
        rowKey={(o) => o.id}
        loading={isPending}
        emptyMessage="조건에 맞는 주문이 없습니다."
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalCount={data.totalCount} onChange={setPage} />
      )}
    </section>
  )
}

import DataTable, { type Column } from './DataTable'
import { formatDateTime, formatNumber, formatWon } from '../lib/format'
import type { OrderReturn } from '../types/order'

const columns: Column<OrderReturn>[] = [
  { key: 'at', header: '반품일시', render: (r) => formatDateTime(r.returnedAt), width: '140px' },
  { key: 'product', header: '상품', render: (r) => `${r.lineNo}. ${r.productName}`, wrap: true },
  { key: 'qty', header: '수량', render: (r) => formatNumber(r.qty), align: 'right', width: '64px' },
  { key: 'amount', header: '금액', render: (r) => formatWon(r.amount), align: 'right', width: '130px' },
  { key: 'reason', header: '사유', render: (r) => r.reason ?? '-', wrap: true },
]

export default function OrderReturnsTable({ returns }: { returns: OrderReturn[] }) {
  return (
    <DataTable caption="반품 내역" columns={columns} rows={returns} rowKey={(r) => r.id} emptyMessage="반품 내역이 없습니다." />
  )
}

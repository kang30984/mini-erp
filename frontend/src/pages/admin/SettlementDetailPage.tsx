import { Link, useParams } from 'react-router'
import { useSettlementDetails, useSettlementMonth } from '../../api/settlement'
import DataTable, { type Column } from '../../components/DataTable'
import { formatDateTime, formatMonth, formatNumber, formatRate, formatWon } from '../../lib/format'
import type { SettlementDetail } from '../../types/settlement'

const columns: Column<SettlementDetail>[] = [
  { key: 'at', header: '일시', render: (d) => formatDateTime(d.occurredAt), width: '140px' },
  {
    key: 'type',
    header: '구분',
    render: (d) => (d.sourceType === 'SHIP' ? '출고' : <span className="text-danger">반품</span>),
    width: '64px',
  },
  { key: 'order', header: '주문번호', render: (d) => <span className="code">{d.orderNo}</span>, width: '170px' },
  { key: 'product', header: '상품', render: (d) => d.productName, wrap: true },
  { key: 'qty', header: '수량', render: (d) => formatNumber(d.qty), align: 'right', width: '64px' },
  { key: 'amount', header: '금액', render: (d) => formatWon(d.amount), align: 'right', width: '130px' },
  { key: 'rate', header: '요율', render: (d) => formatRate(d.rebateRate), align: 'right', width: '72px' },
  { key: 'rebate', header: '리베이트', render: (d) => formatWon(d.rebateAmount), align: 'right', width: '110px' },
]

export default function SettlementDetailPage() {
  const params = useParams()
  const yyyymm = params.yyyymm ?? ''
  const partnerId = Number(params.partnerId)
  const { data: month } = useSettlementMonth(yyyymm)
  const { data: details, isPending, isError, error } = useSettlementDetails(yyyymm, partnerId)
  const summary = month?.summaries.find((s) => s.partnerId === partnerId)

  return (
    <section>
      <div className="page-header">
        <div>
          <Link to={`/admin/settlements?month=${yyyymm}`} className="back-link">월 정산</Link>
          <h1 className="page-title">
            {summary?.partnerName ?? '거래처'} {formatMonth(yyyymm)} 정산 내역
          </h1>
        </div>
      </div>
      <p className="page-description">
        리베이트는 줄마다 원래 출고일에 적용 중이던 요율로 계산하고 원 단위 미만은 버립니다. 반품은 반품한 달에
        음수로 들어갑니다.
      </p>

      {summary && (
        <dl className="detail-grid totals-grid">
          <div>
            <dt>출고 금액</dt>
            <dd>{formatWon(summary.shipAmount)}</dd>
          </div>
          <div>
            <dt>반품 금액</dt>
            <dd>{summary.returnAmount ? `-${formatWon(summary.returnAmount)}` : '-'}</dd>
          </div>
          <div>
            <dt>순매출</dt>
            <dd className="amount">{formatWon(summary.netAmount)}</dd>
          </div>
          <div>
            <dt>리베이트</dt>
            <dd className="amount">{formatWon(summary.rebateAmount)}</dd>
          </div>
        </dl>
      )}

      {isError && <p className="form-error" role="alert">{error.message}</p>}
      <DataTable
        caption="정산 상세 내역"
        columns={columns}
        rows={details}
        rowKey={(d) => `${d.sourceType}-${d.orderNo}-${d.lineNo}-${d.occurredAt}`}
        loading={isPending}
        emptyMessage="정산 내역이 없습니다."
      />
    </section>
  )
}

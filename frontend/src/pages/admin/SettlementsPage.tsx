import { useState } from 'react'
import { Link } from 'react-router'
import { csvUrl, useRunSettlement, useSettlementMonth } from '../../api/settlement'
import DataTable, { type Column } from '../../components/DataTable'
import Modal from '../../components/Modal'
import StatusBadge from '../../components/StatusBadge'
import { useListParams } from '../../hooks/useListParams'
import { SETTLEMENT_STATUSES, labelOf } from '../../lib/codes'
import { currentMonth, formatDateTime, formatMonth, formatNumber, formatWon, shiftMonth } from '../../lib/format'
import type { SettlementRun, SettlementSummary } from '../../types/settlement'

const KEYS = ['month'] as const

export default function SettlementsPage() {
  const { values, apply } = useListParams(KEYS)
  const thisMonth = currentMonth()
  // 기본은 지난달. 보통 지난달 정산을 마감하러 들어오기 때문
  const month = /^\d{6}$/.test(values.month) ? values.month : shiftMonth(thisMonth, -1)
  const { data, isPending, isError, error } = useSettlementMonth(month)
  const run = useRunSettlement()
  const [confirmClose, setConfirmClose] = useState(false)

  const moveTo = (yyyymm: string) => {
    run.reset()
    apply({ month: yyyymm })
  }

  const columns: Column<SettlementSummary>[] = [
    { key: 'code', header: '거래처 코드', render: (s) => <span className="code">{s.partnerCode}</span>, width: '110px' },
    {
      key: 'name',
      header: '거래처',
      render: (s) => <Link to={`/admin/settlements/${month}/${s.partnerId}`}>{s.partnerName}</Link>,
      wrap: true,
    },
    { key: 'ship', header: '출고 금액', render: (s) => formatWon(s.shipAmount), align: 'right' },
    {
      key: 'return',
      header: '반품 금액',
      render: (s) => (s.returnAmount ? `-${formatWon(s.returnAmount)}` : '-'),
      align: 'right',
    },
    { key: 'net', header: '순매출', render: (s) => formatWon(s.netAmount), align: 'right' },
    { key: 'rebate', header: '리베이트', render: (s) => formatWon(s.rebateAmount), align: 'right' },
  ]

  const runColumns: Column<SettlementRun>[] = [
    { key: 'at', header: '실행 일시', render: (r) => formatDateTime(r.runAt), width: '150px' },
    { key: 'type', header: '구분', render: (r) => (r.runType === 'CLOSE' ? '마감' : '미리보기'), width: '90px' },
    { key: 'rows', header: '처리 건수', render: (r) => formatNumber(r.rowCount), align: 'right' },
    { key: 'ms', header: '소요 시간', render: (r) => `${formatNumber(r.elapsedMs)}ms`, align: 'right' },
    { key: 'by', header: '실행자', render: (r) => r.runByName, width: '120px' },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">월 정산</h1>
      </div>

      <div className="month-bar">
        <button type="button" className="button button-secondary" onClick={() => moveTo(shiftMonth(month, -1))}>
          이전 달
        </button>
        <label className="month-picker">
          <span className="visually-hidden">정산월</span>
          <input
            type="month"
            value={`${month.substring(0, 4)}-${month.substring(4, 6)}`}
            max={`${thisMonth.substring(0, 4)}-${thisMonth.substring(4, 6)}`}
            onChange={(e) => e.target.value && moveTo(e.target.value.replace('-', ''))}
          />
        </label>
        <button
          type="button"
          className="button button-secondary"
          disabled={month >= thisMonth}
          onClick={() => moveTo(shiftMonth(month, 1))}
        >
          다음 달
        </button>
        {data && (
          <StatusBadge value={data.status} label={labelOf(SETTLEMENT_STATUSES, data.status)} />
        )}
        {data?.status === 'CLOSED' && (
          <span className="month-meta">
            {formatDateTime(data.closedAt)} {data.closedByName} 마감
          </span>
        )}
        <div className="month-actions">
          {data?.previewable && (
            <button
              type="button"
              className="button button-secondary"
              disabled={run.isPending}
              onClick={() => run.mutate({ yyyymm: month, close: false })}
            >
              {data.status === 'NONE' ? '미리보기 실행' : '다시 계산'}
            </button>
          )}
          {data?.closable && (
            <button type="button" className="button button-primary" onClick={() => setConfirmClose(true)}>
              마감
            </button>
          )}
        </div>
      </div>

      {data?.status === 'OPEN' && !data.closable && (
        <p className="field-hint month-hint">진행 중인 달은 마감할 수 없습니다. 다음 달이 되면 마감할 수 있습니다.</p>
      )}
      {run.isError && !confirmClose && <p className="form-error" role="alert">{run.error.message}</p>}
      {isError && <p className="form-error" role="alert">{error.message}</p>}

      {data && data.status !== 'NONE' && (
        <dl className="detail-grid totals-grid">
          <div>
            <dt>출고 금액</dt>
            <dd>{formatWon(data.totals.shipAmount)}</dd>
          </div>
          <div>
            <dt>반품 금액</dt>
            <dd>{data.totals.returnAmount ? `-${formatWon(data.totals.returnAmount)}` : '-'}</dd>
          </div>
          <div>
            <dt>순매출</dt>
            <dd className="amount">{formatWon(data.totals.netAmount)}</dd>
          </div>
          <div>
            <dt>리베이트 합계</dt>
            <dd className="amount">{formatWon(data.totals.rebateAmount)}</dd>
          </div>
        </dl>
      )}

      <div className="section-header">
        <h2 className="section-title">{formatMonth(month)} 거래처별 정산</h2>
        {data && data.summaries.length > 0 && (
          <a className="button button-small" href={csvUrl(month)} download>
            CSV 내려받기
          </a>
        )}
      </div>
      <DataTable
        caption={`${formatMonth(month)} 거래처별 정산`}
        columns={columns}
        rows={data?.summaries}
        rowKey={(s) => s.partnerId}
        loading={isPending}
        emptyMessage={
          data?.status === 'NONE'
            ? '아직 정산을 실행하지 않았습니다. 미리보기를 실행하면 출고·반품 내역으로 결과를 계산합니다.'
            : '이 달에는 출고나 반품 내역이 없습니다.'
        }
      />

      {data && data.runs.length > 0 && (
        <>
          <h2 className="section-title">실행 이력</h2>
          <DataTable
            caption="정산 실행 이력"
            columns={runColumns}
            rows={data.runs}
            rowKey={(r) => r.id}
            emptyMessage="실행 이력이 없습니다."
          />
        </>
      )}

      <Modal open={confirmClose} title={`${formatMonth(month)} 정산 마감`} onClose={() => setConfirmClose(false)}>
        <p>
          마감할 때 최신 출고·반품 내역으로 한 번 더 계산한 뒤 확정합니다. 마감하면 다시 계산할 수 없고, 이 달에 속한
          출고·반품 데이터도 수정할 수 없습니다.
        </p>
        {run.isError && <p className="form-error" role="alert">{run.error.message}</p>}
        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={() => setConfirmClose(false)}>
            닫기
          </button>
          <button
            type="button"
            className="button button-primary"
            disabled={run.isPending}
            onClick={() =>
              run.mutate({ yyyymm: month, close: true }, { onSuccess: () => setConfirmClose(false) })
            }
          >
            마감
          </button>
        </div>
      </Modal>
    </section>
  )
}

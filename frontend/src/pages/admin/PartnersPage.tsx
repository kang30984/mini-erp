import { useState } from 'react'
import { useGrades, usePartners } from '../../api/master'
import DataTable, { type Column } from '../../components/DataTable'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import StatusBadge from '../../components/StatusBadge'
import { useListParams } from '../../hooks/useListParams'
import { PARTNER_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime } from '../../lib/format'
import type { Partner } from '../../types/master'
import PartnerFormModal from './PartnerFormModal'

const KEYS = ['keyword', 'gradeCode', 'status'] as const

export default function PartnersPage() {
  const { values, page, apply, setPage, queryKey } = useListParams(KEYS)
  const { data, isPending, isError, error } = usePartners({ ...values, page })
  const { data: grades } = useGrades()
  const [editing, setEditing] = useState<Partner | null | undefined>(undefined)

  const columns: Column<Partner>[] = [
    { key: 'code', header: '거래처 코드', render: (p) => <span className="code">{p.code}</span>, width: '110px' },
    { key: 'name', wrap: true, header: '거래처명', render: (p) => p.name },
    { key: 'grade', header: '등급', render: (p) => p.gradeName, width: '100px' },
    {
      key: 'status',
      header: '상태',
      render: (p) => <StatusBadge value={p.status} label={labelOf(PARTNER_STATUSES, p.status)} />,
      width: '100px',
    },
    { key: 'updatedAt', header: '수정일시', render: (p) => formatDateTime(p.updatedAt), width: '140px' },
    {
      key: 'actions',
      header: '관리',
      render: (p) => (
        <button type="button" className="button button-small" onClick={() => setEditing(p)}>
          수정
        </button>
      ),
      align: 'center',
      width: '72px',
    },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">거래처</h1>
        <button type="button" className="button button-primary" onClick={() => setEditing(null)}>
          거래처 등록
        </button>
      </div>

      <form
        key={queryKey}
        className="search-bar"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          apply({
            keyword: String(f.get('keyword') ?? ''),
            gradeCode: String(f.get('gradeCode') ?? ''),
            status: String(f.get('status') ?? ''),
          })
        }}
      >
        <input name="keyword" defaultValue={values.keyword} placeholder="거래처명 또는 코드" aria-label="검색어" />
        <select name="gradeCode" defaultValue={values.gradeCode} aria-label="등급">
          <option value="">전체 등급</option>
          {grades?.map((g) => (
            <option key={g.gradeCode} value={g.gradeCode}>{g.name}</option>
          ))}
        </select>
        <select name="status" defaultValue={values.status} aria-label="거래 상태">
          <option value="">전체 상태</option>
          {PARTNER_STATUSES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
        <button type="submit" className="button button-secondary">검색</button>
      </form>

      {isError && <p className="form-error" role="alert">{error.message}</p>}

      <DataTable
        caption="거래처 목록"
        columns={columns}
        rows={data?.items}
        rowKey={(p) => p.id}
        loading={isPending}
        emptyMessage="조건에 맞는 거래처가 없습니다."
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalCount={data.totalCount} onChange={setPage} />
      )}

      <Modal
        open={editing !== undefined}
        title={editing ? '거래처 수정' : '거래처 등록'}
        onClose={() => setEditing(undefined)}
      >
        <PartnerFormModal partner={editing ?? null} onDone={() => setEditing(undefined)} />
      </Modal>
    </section>
  )
}

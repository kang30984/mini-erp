import { useState } from 'react'
import { useAdminProducts } from '../../api/master'
import DataTable, { type Column } from '../../components/DataTable'
import Modal from '../../components/Modal'
import Pagination from '../../components/Pagination'
import StatusBadge from '../../components/StatusBadge'
import { useListParams } from '../../hooks/useListParams'
import { CATEGORIES, PRODUCT_STATUSES, labelOf } from '../../lib/codes'
import { formatDateTime, formatNumber, formatWon } from '../../lib/format'
import type { Product } from '../../types/master'
import ProductFormModal from './ProductFormModal'

const KEYS = ['keyword', 'category', 'status'] as const

export default function ProductsPage() {
  const { values, page, apply, setPage, queryKey } = useListParams(KEYS)
  const { data, isPending, isError, error } = useAdminProducts({ ...values, page })
  const [editing, setEditing] = useState<Product | null | undefined>(undefined)

  const columns: Column<Product>[] = [
    { key: 'sku', header: '상품 코드', render: (p) => <span className="code">{p.sku}</span>, width: '130px' },
    { key: 'name', wrap: true, header: '상품명', render: (p) => p.name },
    { key: 'category', header: '분류', render: (p) => labelOf(CATEGORIES, p.category), width: '100px' },
    { key: 'unitPrice', header: '단가', render: (p) => formatWon(p.unitPrice), align: 'right', width: '130px' },
    { key: 'stockQty', header: '재고', render: (p) => formatNumber(p.stockQty), align: 'right', width: '80px' },
    {
      key: 'status',
      header: '상태',
      render: (p) => <StatusBadge value={p.status} label={labelOf(PRODUCT_STATUSES, p.status)} />,
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
        <h1 className="page-title">상품</h1>
        <button type="button" className="button button-primary" onClick={() => setEditing(null)}>
          상품 등록
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
            category: String(f.get('category') ?? ''),
            status: String(f.get('status') ?? ''),
          })
        }}
      >
        <input name="keyword" defaultValue={values.keyword} placeholder="상품명 또는 상품 코드" aria-label="검색어" />
        <select name="category" defaultValue={values.category} aria-label="분류">
          <option value="">전체 분류</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </select>
        <select name="status" defaultValue={values.status} aria-label="판매 상태">
          <option value="">전체 상태</option>
          {PRODUCT_STATUSES.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
        <button type="submit" className="button button-secondary">검색</button>
      </form>

      {isError && <p className="form-error" role="alert">{error.message}</p>}

      <DataTable
        caption="상품 목록"
        columns={columns}
        rows={data?.items}
        rowKey={(p) => p.id}
        loading={isPending}
        emptyMessage="조건에 맞는 상품이 없습니다. 검색 조건을 바꾸거나 새 상품을 등록해 주세요."
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalCount={data.totalCount} onChange={setPage} />
      )}

      <Modal
        open={editing !== undefined}
        title={editing ? '상품 수정' : '상품 등록'}
        onClose={() => setEditing(undefined)}
      >
        <ProductFormModal product={editing ?? null} onDone={() => setEditing(undefined)} />
      </Modal>
    </section>
  )
}

import { useState } from 'react'
import { Link } from 'react-router'
import { useMarketProducts } from '../../api/market'
import DataTable, { type Column } from '../../components/DataTable'
import Pagination from '../../components/Pagination'
import { useCart } from '../../features/cart/CartContext'
import { useListParams } from '../../hooks/useListParams'
import { CATEGORIES, labelOf } from '../../lib/codes'
import { formatNumber, formatWon } from '../../lib/format'
import type { Product } from '../../types/master'

const KEYS = ['keyword', 'category'] as const

export default function MarketProductsPage() {
  const { values, page, apply, setPage, queryKey } = useListParams(KEYS)
  const { data, isPending, isError, error } = useMarketProducts({ ...values, page })
  const cart = useCart()
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [notice, setNotice] = useState('')

  const qtyOf = (id: number) => quantities[id] ?? 1

  const addToCart = (product: Product) => {
    const qty = qtyOf(product.id)
    cart.add(product, qty)
    setNotice(`${product.name} ${qty}개를 장바구니에 담았습니다.`)
  }

  const columns: Column<Product>[] = [
    {
      key: 'name',
      wrap: true,
      header: '상품',
      render: (p) => (
        <div className="cell-stack">
          <span>{p.name}</span>
          <span className="code">{p.sku}</span>
        </div>
      ),
    },
    { key: 'category', header: '분류', render: (p) => labelOf(CATEGORIES, p.category), width: '100px' },
    { key: 'price', header: '단가', render: (p) => formatWon(p.unitPrice), align: 'right', width: '130px' },
    {
      key: 'stock',
      header: '재고',
      render: (p) => (p.stockQty === 0 ? <span className="text-danger">품절</span> : formatNumber(p.stockQty)),
      align: 'right',
      width: '80px',
    },
    {
      key: 'qty',
      header: '수량',
      render: (p) => (
        <input
          type="number"
          className="qty-input"
          min={1}
          max={Math.min(999, p.stockQty)}
          value={qtyOf(p.id)}
          disabled={p.stockQty === 0}
          aria-label={`${p.name} 주문 수량`}
          onChange={(e) => setQuantities({ ...quantities, [p.id]: Math.max(1, Number(e.target.value) || 1) })}
        />
      ),
      align: 'center',
      width: '96px',
    },
    {
      key: 'add',
      header: '',
      render: (p) => (
        <button
          type="button"
          className="button button-small"
          disabled={p.stockQty === 0}
          onClick={() => addToCart(p)}
        >
          담기
        </button>
      ),
      align: 'center',
      width: '72px',
    },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">상품 주문</h1>
        <Link to="/market/cart" className="button button-primary">
          장바구니 {cart.count > 0 && `(${cart.count})`}
        </Link>
      </div>

      <form
        key={queryKey}
        className="search-bar"
        onSubmit={(e) => {
          e.preventDefault()
          const f = new FormData(e.currentTarget)
          apply({ keyword: String(f.get('keyword') ?? ''), category: String(f.get('category') ?? '') })
        }}
      >
        <input name="keyword" defaultValue={values.keyword} placeholder="상품명 또는 상품 코드" aria-label="검색어" />
        <select name="category" defaultValue={values.category} aria-label="분류">
          <option value="">전체 분류</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </select>
        <button type="submit" className="button button-secondary">검색</button>
      </form>

      <p className="notice" role="status" aria-live="polite">{notice}</p>
      {isError && <p className="form-error" role="alert">{error.message}</p>}

      <DataTable
        caption="주문 가능한 상품 목록"
        columns={columns}
        rows={data?.items}
        rowKey={(p) => p.id}
        loading={isPending}
        emptyMessage="조건에 맞는 상품이 없습니다."
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalCount={data.totalCount} onChange={setPage} />
      )}
    </section>
  )
}

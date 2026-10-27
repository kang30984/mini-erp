import { Link, useNavigate } from 'react-router'
import { useCreateOrder } from '../../api/market'
import DataTable, { type Column } from '../../components/DataTable'
import { useCart, type CartLine } from '../../features/cart/CartContext'
import { formatWon } from '../../lib/format'

export default function CartPage() {
  const cart = useCart()
  const createOrder = useCreateOrder()
  const navigate = useNavigate()

  const overStock = cart.lines.filter((l) => l.qty > l.stockQty)

  const submit = () => {
    createOrder.mutate(
      { items: cart.lines.map((l) => ({ productId: l.productId, qty: l.qty })) },
      {
        onSuccess: (order) => {
          cart.clear()
          navigate(`/market/orders/${order.id}`, { state: { created: true } })
        },
      },
    )
  }

  const columns: Column<CartLine>[] = [
    {
      key: 'name',
      wrap: true,
      header: '상품',
      render: (l) => (
        <div className="cell-stack">
          <span>{l.name}</span>
          <span className="code">{l.sku}</span>
        </div>
      ),
    },
    { key: 'price', header: '단가', render: (l) => formatWon(l.unitPrice), align: 'right', width: '130px' },
    {
      key: 'qty',
      header: '수량',
      render: (l) => (
        <input
          type="number"
          className="qty-input"
          min={1}
          max={999}
          value={l.qty}
          aria-label={`${l.name} 수량`}
          aria-invalid={l.qty > l.stockQty ? 'true' : 'false'}
          onChange={(e) => cart.update(l.productId, Number(e.target.value) || 1)}
        />
      ),
      align: 'center',
      width: '96px',
    },
    { key: 'amount', header: '금액', render: (l) => formatWon(l.unitPrice * l.qty), align: 'right', width: '140px' },
    {
      key: 'remove',
      header: '',
      render: (l) => (
        <button type="button" className="button button-small" onClick={() => cart.remove(l.productId)}>
          빼기
        </button>
      ),
      align: 'center',
      width: '72px',
    },
  ]

  return (
    <section>
      <div className="page-header">
        <h1 className="page-title">장바구니</h1>
      </div>

      <DataTable
        caption="장바구니에 담긴 상품"
        columns={columns}
        rows={cart.lines}
        rowKey={(l) => l.productId}
        emptyMessage={
          <>
            장바구니가 비어 있습니다. <Link to="/market/products">상품 목록</Link>에서 주문할 상품을 담아 주세요.
          </>
        }
      />

      {cart.lines.length > 0 && (
        <div className="order-summary">
          <dl>
            <dt>예상 주문 금액</dt>
            <dd>{formatWon(cart.total)}</dd>
          </dl>
          <p className="field-hint">주문 금액은 주문하는 시점의 단가로 다시 계산됩니다.</p>
          {overStock.length > 0 && (
            <p className="form-error" role="alert">
              {overStock.map((l) => `${l.name}(재고 ${l.stockQty}개)`).join(', ')}의 수량이 담을 때 확인한 재고보다 많습니다.
            </p>
          )}
          {createOrder.isError && <p className="form-error" role="alert">{createOrder.error.message}</p>}
          <button
            type="button"
            className="button button-primary"
            onClick={submit}
            disabled={createOrder.isPending}
          >
            {createOrder.isPending ? '주문하는 중' : `${cart.count}개 품목 주문하기`}
          </button>
        </div>
      )}
    </section>
  )
}

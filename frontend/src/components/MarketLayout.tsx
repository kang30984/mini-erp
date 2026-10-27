import { useMe } from '../api/auth'
import { CartProvider, useCart } from '../features/cart/CartContext'
import AppLayout from './AppLayout'

function MarketMenus() {
  const cart = useCart()
  return (
    <AppLayout
      menus={[
        { to: '/market/products', label: '상품 주문' },
        { to: '/market/cart', label: '장바구니', count: cart.count },
        { to: '/market/orders', label: '주문 내역', matchChildren: true },
      ]}
    />
  )
}

/** 장바구니는 거래처 화면에서만 쓰므로 여기서 Provider를 감싼다 */
export default function MarketLayout() {
  const { data: me } = useMe()
  if (!me) return null
  return (
    <CartProvider userId={me.id}>
      <MarketMenus />
    </CartProvider>
  )
}

import { createContext, useContext, useEffect, useMemo, useReducer, type ReactNode } from 'react'
import type { Product } from '../../types/master'

export interface CartLine {
  productId: number
  sku: string
  name: string
  unitPrice: number
  stockQty: number
  qty: number
}

const MAX_QTY = 999
const STORAGE_PREFIX = 'minierp.cart.'

type Action =
  | { type: 'add'; product: Product; qty: number }
  | { type: 'update'; productId: number; qty: number }
  | { type: 'remove'; productId: number }
  | { type: 'clear' }

function clampQty(qty: number) {
  return Math.min(MAX_QTY, Math.max(1, Math.floor(qty)))
}

function reducer(lines: CartLine[], action: Action): CartLine[] {
  switch (action.type) {
    case 'add': {
      const { product, qty } = action
      const existing = lines.find((l) => l.productId === product.id)
      if (existing) {
        // 같은 상품을 다시 담으면 줄을 늘리지 않고 수량을 합친다 (서버도 같은 상품 두 줄을 거부함)
        return lines.map((l) =>
          l.productId === product.id
            ? { ...l, qty: clampQty(l.qty + qty), unitPrice: product.unitPrice, stockQty: product.stockQty }
            : l,
        )
      }
      return [
        ...lines,
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          unitPrice: product.unitPrice,
          stockQty: product.stockQty,
          qty: clampQty(qty),
        },
      ]
    }
    case 'update':
      return lines.map((l) => (l.productId === action.productId ? { ...l, qty: clampQty(action.qty) } : l))
    case 'remove':
      return lines.filter((l) => l.productId !== action.productId)
    case 'clear':
      return []
  }
}

function load(key: string): CartLine[] {
  try {
    const raw = sessionStorage.getItem(key)
    return raw ? (JSON.parse(raw) as CartLine[]) : []
  } catch {
    return []
  }
}

/** 로그아웃할 때 장바구니를 모두 비운다. 같은 PC에서 다른 거래처로 로그인해도 남지 않도록 */
export function clearAllCarts() {
  Object.keys(sessionStorage)
    .filter((key) => key.startsWith(STORAGE_PREFIX))
    .forEach((key) => sessionStorage.removeItem(key))
}

interface CartContextValue {
  lines: CartLine[]
  count: number
  total: number
  add: (product: Product, qty: number) => void
  update: (productId: number, qty: number) => void
  remove: (productId: number) => void
  clear: () => void
}

const CartContext = createContext<CartContextValue | null>(null)

interface Props {
  userId: number
  children: ReactNode
}

export function CartProvider({ userId, children }: Props) {
  const storageKey = `${STORAGE_PREFIX}${userId}`
  const [lines, dispatch] = useReducer(reducer, storageKey, load)

  useEffect(() => {
    sessionStorage.setItem(storageKey, JSON.stringify(lines))
  }, [storageKey, lines])

  const value = useMemo<CartContextValue>(
    () => ({
      lines,
      count: lines.length,
      total: lines.reduce((sum, l) => sum + l.unitPrice * l.qty, 0),
      add: (product, qty) => dispatch({ type: 'add', product, qty }),
      update: (productId, qty) => dispatch({ type: 'update', productId, qty }),
      remove: (productId) => dispatch({ type: 'remove', productId }),
      clear: () => dispatch({ type: 'clear' }),
    }),
    [lines],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart는 CartProvider 안에서만 쓸 수 있습니다.')
  return context
}

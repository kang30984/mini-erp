export type OrderStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'CANCELED' | 'SHIPPED'

export interface OrderSummary {
  id: number
  orderNo: string
  status: OrderStatus
  totalAmount: number
  itemCount: number
  firstProductName: string
  orderedAt: string
}

export interface OrderItem {
  lineNo: number
  productId: number
  sku: string
  productName: string
  qty: number
  unitPrice: number
  amount: number
}

export interface OrderHistory {
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  reason: string | null
  changedByName: string
  changedAt: string
}

export interface OrderDetail {
  id: number
  orderNo: string
  partnerId: number
  partnerName: string
  partnerGradeCode: string
  status: OrderStatus
  totalAmount: number
  orderedAt: string
  approvedAt: string | null
  shippedAt: string | null
  items: OrderItem[]
  histories: OrderHistory[]
}

export interface CreateOrder {
  items: { productId: number; qty: number }[]
}

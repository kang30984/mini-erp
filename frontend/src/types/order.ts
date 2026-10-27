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
  returnedQty: number
}

export interface OrderReturn {
  id: number
  lineNo: number
  productName: string
  qty: number
  amount: number
  reason: string | null
  createdByName: string
  returnedAt: string
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
  version: number
  items: OrderItem[]
  returns: OrderReturn[]
  histories: OrderHistory[]
}

export interface AdminOrderSummary {
  id: number
  orderNo: string
  partnerName: string
  status: OrderStatus
  totalAmount: number
  itemCount: number
  firstProductName: string
  orderedAt: string
}

export type OrderAction = 'approve' | 'reject' | 'ship' | 'cancel'

export interface CreateOrder {
  items: { productId: number; qty: number }[]
}

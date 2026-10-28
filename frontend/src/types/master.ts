export type ProductStatus = 'ON_SALE' | 'STOPPED'
export type PartnerStatus = 'ACTIVE' | 'INACTIVE'
export type PolicyState = 'CURRENT' | 'SCHEDULED' | 'ENDED'

export interface Product {
  id: number
  sku: string
  name: string
  category: string
  unitPrice: number
  stockQty: number
  status: ProductStatus
  updatedAt: string
}

export interface ProductForm {
  sku: string
  name: string
  category: string
  unitPrice: number
  stockQty: number
  status: ProductStatus
  /** 수정할 때만 보낸다. 화면이 상품을 불러왔을 때의 재고. 서버는 DB 재고가 이 값과 같을 때만 재고를 바꾼다 */
  baseStockQty?: number
}

export interface Partner {
  id: number
  code: string
  name: string
  gradeCode: string
  gradeName: string
  status: PartnerStatus
  updatedAt: string
}

export interface PartnerForm {
  code: string
  name: string
  gradeCode: string
  status: PartnerStatus
}

export interface Grade {
  gradeCode: string
  name: string
  currentRate: number | null
  nextRate: number | null
  nextValidFrom: string | null
}

export interface RebatePolicy {
  id: number
  gradeCode: string
  gradeName: string
  rebateRate: number
  validFrom: string
  validTo: string | null
  state: PolicyState
}

export interface RebatePolicyForm {
  gradeCode: string
  rebateRate: number
  validFrom: string
}

export interface Option {
  value: string
  label: string
}

export const CATEGORIES: Option[] = [
  { value: 'NOTEBOOK', label: '노트북' },
  { value: 'DESKTOP', label: '데스크톱' },
  { value: 'MONITOR', label: '모니터' },
  { value: 'SERVER', label: '서버' },
  { value: 'STORAGE', label: '스토리지' },
  { value: 'NETWORK', label: '네트워크' },
  { value: 'SOFTWARE', label: '소프트웨어' },
]

export const PRODUCT_STATUSES: Option[] = [
  { value: 'ON_SALE', label: '판매 중' },
  { value: 'STOPPED', label: '판매 중지' },
]

export const PARTNER_STATUSES: Option[] = [
  { value: 'ACTIVE', label: '거래 중' },
  { value: 'INACTIVE', label: '거래 중지' },
]

export const ORDER_STATUSES: Option[] = [
  { value: 'REQUESTED', label: '승인 대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'SHIPPED', label: '출고 완료' },
  { value: 'REJECTED', label: '반려' },
  { value: 'CANCELED', label: '취소' },
]

/** 처리 이력에 표시할 이름. 상태 이름(승인 대기)이 아니라 일어난 일(주문 요청)로 보여 준다 */
export const ORDER_EVENTS: Option[] = [
  { value: 'REQUESTED', label: '주문 요청' },
  { value: 'APPROVED', label: '승인' },
  { value: 'SHIPPED', label: '출고' },
  { value: 'REJECTED', label: '반려' },
  { value: 'CANCELED', label: '취소' },
]

export const POLICY_STATES: Option[] = [
  { value: 'CURRENT', label: '적용 중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

export function labelOf(options: Option[], value: string | null | undefined): string {
  if (!value) return '-'
  return options.find((o) => o.value === value)?.label ?? value
}

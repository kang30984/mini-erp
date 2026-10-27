export type SettlementStatus = 'NONE' | 'OPEN' | 'CLOSED'

export interface SettlementSummary {
  partnerId: number
  partnerCode: string
  partnerName: string
  shipAmount: number
  returnAmount: number
  netAmount: number
  rebateAmount: number
}

export interface SettlementRun {
  id: number
  yyyymm: string
  runType: 'PREVIEW' | 'CLOSE'
  rowCount: number | null
  elapsedMs: number | null
  result: string
  runByName: string
  runAt: string
}

export interface SettlementMonth {
  yyyymm: string
  status: SettlementStatus
  closedAt: string | null
  closedByName: string | null
  previewable: boolean
  closable: boolean
  summaries: SettlementSummary[]
  totals: { shipAmount: number; returnAmount: number; netAmount: number; rebateAmount: number }
  runs: SettlementRun[]
}

export interface SettlementDetail {
  sourceType: 'SHIP' | 'RETURN'
  orderNo: string
  lineNo: number
  productName: string
  qty: number
  amount: number
  rebateRate: number
  rebateAmount: number
  occurredAt: string
}

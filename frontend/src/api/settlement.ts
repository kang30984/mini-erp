import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { SettlementDetail, SettlementMonth } from '../types/settlement'

export function useSettlementMonth(yyyymm: string) {
  return useQuery({
    queryKey: ['admin', 'settlement', yyyymm],
    queryFn: () => request<SettlementMonth>('GET', `/api/admin/settlements/${yyyymm}`),
    enabled: /^\d{6}$/.test(yyyymm),
  })
}

export function useSettlementDetails(yyyymm: string, partnerId: number) {
  return useQuery({
    queryKey: ['admin', 'settlement', yyyymm, 'partner', partnerId],
    queryFn: () => request<SettlementDetail[]>('GET', `/api/admin/settlements/${yyyymm}/partners/${partnerId}`),
  })
}

export function useRunSettlement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ yyyymm, close }: { yyyymm: string; close: boolean }) =>
      request<SettlementMonth>('POST', `/api/admin/settlements/${yyyymm}/${close ? 'close' : 'preview'}`),
    onSuccess: (month) => {
      queryClient.setQueryData(['admin', 'settlement', month.yyyymm], month)
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlement', month.yyyymm, 'partner'] })
    },
  })
}

export function csvUrl(yyyymm: string) {
  return `/api/admin/settlements/${yyyymm}/csv`
}

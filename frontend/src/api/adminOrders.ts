import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, toQuery } from './client'
import type { PageResponse } from '../types/common'
import type { AdminOrderSummary, OrderAction, OrderDetail } from '../types/order'

export interface AdminOrderSearch {
  keyword?: string
  status?: string
  from?: string
  to?: string
  page?: number
}

export function useAdminOrders(search: AdminOrderSearch) {
  return useQuery({
    queryKey: ['admin', 'orders', search],
    queryFn: () => request<PageResponse<AdminOrderSummary>>('GET', `/api/admin/orders${toQuery({ ...search })}`),
    placeholderData: keepPreviousData,
  })
}

export function useOrderStatusCounts() {
  return useQuery({
    queryKey: ['admin', 'orders', 'status-counts'],
    queryFn: () => request<Record<string, number>>('GET', '/api/admin/orders/status-counts'),
  })
}

export function useAdminOrder(id: number) {
  return useQuery({
    queryKey: ['admin', 'order', id],
    queryFn: () => request<OrderDetail>('GET', `/api/admin/orders/${id}`),
    enabled: Number.isFinite(id),
  })
}

function useOrderMutation<V>(fn: (vars: V) => Promise<OrderDetail>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (order) => {
      queryClient.setQueryData(['admin', 'order', order.id], order)
      queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
    },
    onError: (_error, vars) => {
      // 다른 사용자가 먼저 처리했을 수 있으니 최신 상태를 다시 불러온다
      const id = (vars as { id: number }).id
      queryClient.invalidateQueries({ queryKey: ['admin', 'order', id] })
    },
  })
}

export function useOrderAction() {
  return useOrderMutation(
    ({ id, action, version, reason }: { id: number; action: OrderAction; version: number; reason?: string }) =>
      request<OrderDetail>('POST', `/api/admin/orders/${id}/${action}`, { version, reason }),
  )
}

export function useRegisterReturn() {
  return useOrderMutation(
    ({ id, lineNo, qty, reason }: { id: number; lineNo: number; qty: number; reason: string }) =>
      request<OrderDetail>('POST', `/api/admin/orders/${id}/returns`, { lineNo, qty, reason }),
  )
}

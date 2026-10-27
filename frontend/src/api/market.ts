import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, toQuery } from './client'
import type { PageResponse } from '../types/common'
import type { Product } from '../types/master'
import type { CreateOrder, OrderDetail, OrderSummary } from '../types/order'

export interface MarketProductSearch {
  keyword?: string
  category?: string
  page?: number
}

export function useMarketProducts(search: MarketProductSearch) {
  return useQuery({
    queryKey: ['market', 'products', search],
    queryFn: () => request<PageResponse<Product>>('GET', `/api/market/products${toQuery({ ...search })}`),
    placeholderData: keepPreviousData,
  })
}

export interface MyOrderSearch {
  status?: string
  page?: number
}

export function useMyOrders(search: MyOrderSearch) {
  return useQuery({
    queryKey: ['market', 'orders', search],
    queryFn: () => request<PageResponse<OrderSummary>>('GET', `/api/market/orders${toQuery({ ...search })}`),
    placeholderData: keepPreviousData,
  })
}

export function useMyOrder(id: number) {
  return useQuery({
    queryKey: ['market', 'order', id],
    queryFn: () => request<OrderDetail>('GET', `/api/market/orders/${id}`),
    enabled: Number.isFinite(id),
  })
}

export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateOrder) => request<OrderDetail>('POST', '/api/market/orders', body),
    onSuccess: (order) => {
      queryClient.setQueryData(['market', 'order', order.id], order)
      queryClient.invalidateQueries({ queryKey: ['market', 'orders'] })
    },
  })
}

export function useCancelOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request<OrderDetail>('POST', `/api/market/orders/${id}/cancel`),
    onSuccess: (order) => {
      queryClient.setQueryData(['market', 'order', order.id], order)
      queryClient.invalidateQueries({ queryKey: ['market', 'orders'] })
    },
  })
}

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, toQuery } from './client'
import type { PageResponse } from '../types/common'
import type {
  Grade,
  Partner,
  PartnerForm,
  Product,
  ProductForm,
  RebatePolicy,
  RebatePolicyForm,
} from '../types/master'

// ---------- 상품 ----------

export interface ProductSearch {
  keyword?: string
  category?: string
  status?: string
  page?: number
}

export function useAdminProducts(search: ProductSearch) {
  return useQuery({
    queryKey: ['admin', 'products', search],
    queryFn: () => request<PageResponse<Product>>('GET', `/api/admin/products${toQuery({ ...search })}`),
    placeholderData: keepPreviousData,
  })
}

export function useSaveProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, form }: { id?: number; form: ProductForm }) =>
      id
        ? request<Product>('PUT', `/api/admin/products/${id}`, form)
        : request<Product>('POST', '/api/admin/products', form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
      queryClient.invalidateQueries({ queryKey: ['market', 'products'] })
    },
  })
}

// ---------- 거래처 ----------

export interface PartnerSearch {
  keyword?: string
  gradeCode?: string
  status?: string
  page?: number
}

export function usePartners(search: PartnerSearch) {
  return useQuery({
    queryKey: ['admin', 'partners', search],
    queryFn: () => request<PageResponse<Partner>>('GET', `/api/admin/partners${toQuery({ ...search })}`),
    placeholderData: keepPreviousData,
  })
}

export function useSavePartner() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, form }: { id?: number; form: PartnerForm }) =>
      id
        ? request<Partner>('PUT', `/api/admin/partners/${id}`, form)
        : request<Partner>('POST', '/api/admin/partners', form),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'partners'] }),
  })
}

// ---------- 등급·리베이트 정책 ----------

export function useGrades() {
  return useQuery({
    queryKey: ['admin', 'grades'],
    queryFn: () => request<Grade[]>('GET', '/api/admin/grades'),
    staleTime: 60_000,
  })
}

export function useRebatePolicies(gradeCode: string) {
  return useQuery({
    queryKey: ['admin', 'rebate-policies', gradeCode],
    queryFn: () => request<RebatePolicy[]>('GET', `/api/admin/rebate-policies${toQuery({ gradeCode })}`),
  })
}

export function useRegisterRebatePolicy() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (form: RebatePolicyForm) => request<RebatePolicy[]>('POST', '/api/admin/rebate-policies', form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'rebate-policies'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'grades'] })
    },
  })
}

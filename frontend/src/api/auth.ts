import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, hasCsrfCookie, request } from './client'
import type { LoginForm, Me } from '../types/auth'

const ME_KEY = ['auth', 'me'] as const

async function ensureCsrfCookie() {
  if (!hasCsrfCookie()) {
    await request<void>('GET', '/api/auth/csrf')
  }
}

async function fetchMe(): Promise<Me | null> {
  try {
    return await request<Me>('GET', '/api/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: fetchMe,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (form: LoginForm) => {
      await ensureCsrfCookie()
      return request<Me>('POST', '/api/auth/login', form)
    },
    onSuccess: (me) => {
      queryClient.setQueryData(ME_KEY, me)
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      await ensureCsrfCookie()
      await request<void>('POST', '/api/auth/logout')
    },
    onSettled: () => {
      // 다른 사용자 데이터가 캐시에 남지 않도록 전부 비운 뒤 로그인 상태만 null로 둔다
      queryClient.clear()
      queryClient.setQueryData(ME_KEY, null)
    },
  })
}

import { useSearchParams } from 'react-router'

/**
 * 목록 화면의 검색 조건과 페이지 번호를 URL 쿼리로 관리한다.
 * 새로 고침하거나 뒤로 가기를 해도 보던 조건이 유지된다.
 */
export function useListParams<K extends string>(keys: readonly K[]) {
  const [searchParams, setSearchParams] = useSearchParams()

  const values = Object.fromEntries(keys.map((k) => [k, searchParams.get(k) ?? ''])) as Record<K, string>
  const page = Math.max(1, Number(searchParams.get('page')) || 1)

  /** 검색 조건을 바꾸면 페이지는 1로 돌아간다 */
  const apply = (next: Partial<Record<K, string>>) => {
    const params = new URLSearchParams()
    keys.forEach((key) => {
      const value = (key in next ? next[key] : values[key]) ?? ''
      if (value) params.set(key, value)
    })
    setSearchParams(params)
  }

  const setPage = (nextPage: number) => {
    const params = new URLSearchParams(searchParams)
    if (nextPage <= 1) params.delete('page')
    else params.set('page', String(nextPage))
    setSearchParams(params)
  }

  return { values, page, apply, setPage, queryKey: searchParams.toString() }
}

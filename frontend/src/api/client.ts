export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export function readCookie(name: string): string | null {
  const found = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
  return found ? decodeURIComponent(found.substring(name.length + 1)) : null
}

export function hasCsrfCookie(): boolean {
  return readCookie(CSRF_COOKIE) !== null
}

function fallbackMessage(status: number): string {
  if (status === 401) return '로그인이 필요합니다.'
  if (status === 403) return '이 작업을 할 권한이 없습니다.'
  if (status >= 500) return '서버에서 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  return '요청을 처리하지 못했습니다.'
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export async function request<T>(method: Method, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = readCookie(CSRF_COOKIE)
    if (token) headers[CSRF_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해 주세요.')
  }

  const text = await response.text()
  let data: unknown
  try {
    data = text ? JSON.parse(text) : undefined
  } catch {
    data = undefined
  }

  if (!response.ok) {
    const error = data as { code?: string; message?: string } | undefined
    throw new ApiError(response.status, error?.message ?? fallbackMessage(response.status), error?.code)
  }
  return data as T
}

type QueryValue = string | number | null | undefined

/** 값이 비어 있는 항목은 빼고 쿼리 문자열을 만든다 */
export function toQuery(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

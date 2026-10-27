export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalCount: number
  totalPages: number
}

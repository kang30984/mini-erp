import type { ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
  align?: 'left' | 'right' | 'center'
  width?: string
  /** 긴 글자를 줄바꿈해도 되는 열 (상품명 등). 나머지 열은 한 줄로 유지 */
  wrap?: boolean
}

interface Props<T> {
  columns: Column<T>[]
  rows: T[] | undefined
  rowKey: (row: T) => string | number
  caption: string
  loading?: boolean
  emptyMessage: ReactNode
}

function cellClass<T>(c: Column<T>) {
  const classes = [c.align ? `align-${c.align}` : '', c.wrap ? 'wrap' : ''].filter(Boolean)
  return classes.length ? classes.join(' ') : undefined
}

export default function DataTable<T>({ columns, rows, rowKey, caption, loading, emptyMessage }: Props<T>) {
  const list = rows ?? []

  return (
    <div className="table-wrap">
      <table className="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col" className={c.align ? `align-${c.align}` : undefined} style={{ width: c.width }}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {list.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                {loading ? '불러오는 중입니다.' : emptyMessage}
              </td>
            </tr>
          ) : (
            list.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((c) => (
                  <td key={c.key} className={cellClass(c)}>
                    {c.render(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

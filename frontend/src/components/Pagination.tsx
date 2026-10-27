interface Props {
  page: number
  totalPages: number
  totalCount: number
  onChange: (page: number) => void
}

const WINDOW = 5

export default function Pagination({ page, totalPages, totalCount, onChange }: Props) {
  const start = Math.max(1, Math.min(page - Math.floor(WINDOW / 2), totalPages - WINDOW + 1))
  const end = Math.min(totalPages, start + WINDOW - 1)
  const pages = Array.from({ length: Math.max(0, end - start + 1) }, (_, i) => start + i)

  return (
    <div className="pagination">
      <span className="pagination-total">총 {totalCount.toLocaleString('ko-KR')}건</span>
      {totalPages > 1 && (
        <nav aria-label="페이지">
          <button type="button" onClick={() => onChange(page - 1)} disabled={page <= 1}>
            이전
          </button>
          {pages.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => onChange(p)}
              aria-current={p === page ? 'page' : undefined}
            >
              {p}
            </button>
          ))}
          <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages}>
            다음
          </button>
        </nav>
      )}
    </div>
  )
}

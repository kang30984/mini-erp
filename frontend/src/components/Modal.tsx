import { useEffect, useRef, type ReactNode } from 'react'

interface Props {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}

/**
 * 브라우저 기본 dialog 요소를 쓴다.
 * showModal()로 열면 배경 클릭 차단, 포커스 가두기, Esc 닫기를 브라우저가 처리해 준다.
 */
export default function Modal({ open, title, onClose, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog ref={ref} className="modal" aria-labelledby="modal-title" onClose={onClose}>
      <div className="modal-header">
        <h2 id="modal-title">{title}</h2>
        <button type="button" className="modal-close" onClick={onClose} aria-label="닫기">
          ✕
        </button>
      </div>
      {/* 닫혀 있을 때는 내용을 그리지 않아서, 다시 열면 입력값이 초기화된다 */}
      {open && <div className="modal-body">{children}</div>}
    </dialog>
  )
}

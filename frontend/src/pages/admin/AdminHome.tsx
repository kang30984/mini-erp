import { Link } from 'react-router'

const LINKS = [
  { to: '/admin/orders?status=REQUESTED', title: '주문 관리', description: '거래처 주문을 승인·반려하고 출고와 반품을 처리합니다.' },
  { to: '/admin/settlements', title: '월 정산', description: '거래처별 출고·반품 금액과 리베이트를 계산하고 마감합니다.' },
  { to: '/admin/products', title: '상품', description: '판매 상품과 단가, 재고를 관리합니다.' },
  { to: '/admin/partners', title: '거래처', description: '거래처 등록과 등급, 거래 상태를 관리합니다.' },
  { to: '/admin/rebate-policies', title: '리베이트 정책', description: '등급별 리베이트율과 변경 예정을 관리합니다.' },
]

export default function AdminHome() {
  return (
    <section>
      <h1 className="page-title">운영 홈</h1>
      <p className="page-description">주문 처리, 정산, 기준정보 관리 화면으로 이동합니다.</p>
      <ul className="link-list">
        {LINKS.map((l) => (
          <li key={l.to}>
            <Link to={l.to}>{l.title}</Link>
            <span>{l.description}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

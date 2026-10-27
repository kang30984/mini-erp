import { Link } from 'react-router'

const LINKS = [
  { to: '/admin/products', title: '상품', description: '판매 상품과 단가, 재고를 관리합니다.' },
  { to: '/admin/partners', title: '거래처', description: '거래처 등록과 등급, 거래 상태를 관리합니다.' },
  { to: '/admin/rebate-policies', title: '리베이트 정책', description: '등급별 리베이트율과 변경 예정을 관리합니다.' },
]

export default function AdminHome() {
  return (
    <section>
      <h1 className="page-title">운영 홈</h1>
      <p className="page-description">주문 승인과 출고, 월 정산 화면은 다음 단계에서 추가됩니다.</p>
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

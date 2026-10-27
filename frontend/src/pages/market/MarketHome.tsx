import { useMe } from '../../api/auth'

export default function MarketHome() {
  const { data: me } = useMe()

  return (
    <section>
      <h1 className="page-title">{me?.partnerName} 주문 홈</h1>
      <p className="page-description">
        상품을 골라 주문하고 진행 상태를 확인하는 화면이 이곳에 추가됩니다.
      </p>
    </section>
  )
}

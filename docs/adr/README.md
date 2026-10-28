# 설계 결정 기록 (ADR)

구현하면서 고민이 필요했던 결정들을 남겨 둔 문서다. 각 문서에는 왜 그 문제를 고민했는지, 어떤 방법들을 비교했는지, 무엇을 골랐고 그 대가가 무엇인지를 적었다.

| 번호 | 제목 |
|---|---|
| [001](001-settlement-in-plpgsql.md) | 월 정산을 PL/pgSQL 프로시저로 계산 |
| [002](002-rebate-rate-and-returns.md) | 리베이트는 출고일 요율, 반품은 반품한 달에 차감 |
| [003](003-closing-rules.md) | 마감은 지난달까지만, 마감한 달은 트리거로 보호 |
| [004](004-settlement-detail-without-fk.md) | 정산 상세 테이블에 외래키를 두지 않기 |
| [005](005-no-requires-new-failure-log.md) | 정산 실패 기록을 별도 트랜잭션으로 남기지 않기 |
| [006](006-approval-locking.md) | 승인 처리의 잠금 순서와 버전 확인 |
| [007](007-return-qty-check.md) | 반품 수량은 잠근 뒤 다음 문장에서 센다 |
| [008](008-session-and-csrf.md) | 세션 로그인과 쿠키 기반 CSRF |
| [009](009-settlement-month-lock.md) | 월 경계에서 마감과 출고가 겹칠 때 정산월 잠금 |
| [010](010-product-stock-compare-and-set.md) | 상품 수정에서 재고는 비교 후 교체 |

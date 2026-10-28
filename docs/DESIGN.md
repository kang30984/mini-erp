# mini-erp 설계 문서

> 개발 전에 작성한 설계 문서다. 구현하면서 달라진 부분은 해당 절에 **[구현]** 으로 표시했고, 결정 이유는 [ADR](adr/README.md)에 있다.

---

## 1. 목적

IT 제품 유통사의 거래처 주문부터 월 정산 마감까지를 작게 다시 만들어 본다.

| 요구 사항 | 이 프로젝트에서 보여 주는 방법 |
|---|---|
| 기간계 업무 이해 | 주문 상태 전이, 마감월 수정 불가, 요율 변경 이력, 마감 후 반품 처리 |
| 프론트엔드·백엔드 개발 | React 화면(주문, 정산, 기준정보)과 Spring Boot API를 같이 만든다 |
| PL/SQL | 월 정산을 PL/pgSQL 프로시저로 만든다 |
| React | 권한별 화면, 목록·검색·페이징, 입력 폼, 장바구니 |
| 정합성과 성능 | 동시 승인 테스트, 10만 건 정산 시간 측정 |

범위에서 뺀 것: 회계 전표, 세금계산서, 결제 연동, 배송 추적

---

## 2. 업무 흐름

```mermaid
flowchart LR
    A["거래처<br/>마켓플레이스 주문"] --> B["주문 요청<br/>REQUESTED"]
    B -->|승인 + 재고 차감| C["승인<br/>APPROVED"]
    B -->|반려| R["반려<br/>REJECTED"]
    B -->|거래처 취소| X["취소<br/>CANCELED"]
    C -->|취소 + 재고 복원| X
    C -->|출고 처리| D["출고<br/>SHIPPED"]
    D -->|부분/전체 반품| E["반품 등록"]
    D --> F["월 정산 마감"]
    E --> F
    F --> G["거래처별 정산 내역<br/>CSV 내려받기"]
```

### 주문 상태 전이

| 현재 상태 | 다음 상태 | 처리하는 사람 | 같이 하는 일 |
|---|---|---|---|
| REQUESTED | APPROVED | 관리자 | 재고 차감. 부족하면 승인 실패 |
| REQUESTED | REJECTED | 관리자 | 반려 사유 필수 |
| REQUESTED | CANCELED | 거래처 | - |
| APPROVED | CANCELED | 관리자 | 재고 복원 |
| APPROVED | SHIPPED | 관리자 | 출고일시 기록. 정산 기준일이 된다 |

상태가 바뀔 때마다 `order_status_history`에 바뀌기 전·후 상태, 처리한 사람, 사유를 남긴다.

---

## 3. 업무 규칙

### 정산 기준

- 출고분은 출고일(`shipped_at`)이 속한 달에 정산한다.
- 반품분은 반품일(`returned_at`)이 속한 달에 차감한다. 원래 출고한 달이 이미 마감됐어도 반품한 달에 마이너스로 들어가므로 마감한 달을 다시 열 필요가 없다.
- 반품 수량 합계는 주문 수량을 넘을 수 없다.

### 리베이트

- 거래처 등급(SILVER, GOLD, PLATINUM)마다 리베이트율이 다르다.
- 요율은 `rebate_policy`에 적용 기간과 함께 이력으로 쌓는다. 월 중간에 요율이 바뀌어도 출고일에 적용 중이던 요율을 쓴다.
- 주문할 때의 거래처 등급을 `sales_order.partner_grade_code`에 저장해 둔다. 나중에 등급이 바뀌어도 지난 주문의 정산 결과가 달라지지 않게 하기 위해서다.
- 반품 리베이트는 원래 출고일의 요율로 되돌린다.
- 금액은 원 단위 정수(`NUMERIC(15,0)`)이고, 리베이트는 주문 줄마다 원 미만을 버린 뒤 합친다.

결정 이유는 [ADR-002](adr/002-rebate-rate-and-returns.md)에 있다.

### 요율 변경

**[구현]** 처음 설계에는 없던 규칙이다. 구현하면서 정했다.

- 새 요율의 적용 시작일은 내일 이후만 받는다. 오늘이나 지난 날짜로 바꾸면 이미 출고된 주문에 적용될 요율이 달라진다.
- 등록하면 기존 마지막 정책의 종료일을 새 시작일로 닫고, 새 정책을 종료일 없이 추가한다.
- 같은 등급을 동시에 바꾸는 요청은 마지막 정책 행을 `FOR UPDATE`로 잠가 차례로 처리한다. 그래도 기간이 겹치면 `EXCLUDE` 제약이 막는다.

### 마감

- 정산월은 `OPEN`, `CLOSED` 두 상태다.
- `OPEN`인 달은 미리보기(다시 계산)를 몇 번이든 할 수 있고, 할 때마다 그 달의 결과를 지우고 새로 만든다.
- `CLOSED`가 되면 다시 계산할 수 없고, 그 달에 속한 출고·반품 데이터도 추가하거나 고칠 수 없다.
- 같은 달을 두 사람이 동시에 마감하지 못하도록 정산월 행을 잠근다.

**[구현]** 아래 규칙을 더했다 ([ADR-003](adr/003-closing-rules.md)).

- 미리보기는 이번 달까지, 마감은 지난달까지만 할 수 있다. 진행 중인 달을 마감하면 남은 기간의 출고·반품을 받을 수 없다.
- 마감하려면 미리보기를 한 번 이상 실행해야 한다.
- 마감할 때도 최신 데이터로 다시 계산한 뒤 닫는다. 미리보기 뒤에 들어온 반품이 빠지지 않는다.
- 출고일에 적용할 리베이트 정책이 없는 주문이 있으면 정산을 멈춘다. 조인에서 조용히 빠지면 틀린 금액으로 마감되기 때문이다.
- 마감한 달의 데이터는 서비스에서 먼저 막고, `sales_return`·`sales_order` 트리거로 한 번 더 막는다.

### 시간대

**[구현]** 업무 기준 시간대는 한국 시간(Asia/Seoul)이다. 애플리케이션은 `Clock` 빈으로 날짜를 계산하고, DB 세션도 접속할 때 `SET TIME ZONE 'Asia/Seoul'`로 맞춘다. 서버를 UTC 환경에 올려도 "오늘"과 "이번 달"이 바뀌지 않게 하기 위해서다. 정산월 경계(출고일 기준)에 바로 영향을 준다.

---

## 4. ERD

```mermaid
erDiagram
    PARTNER ||--o{ APP_USER : "has"
    PARTNER ||--o{ SALES_ORDER : "places"
    PARTNER_GRADE ||--o{ PARTNER : "classifies"
    PARTNER_GRADE ||--o{ REBATE_POLICY : "has"
    PRODUCT ||--o{ SALES_ORDER_ITEM : "ordered"
    SALES_ORDER ||--|{ SALES_ORDER_ITEM : "contains"
    SALES_ORDER ||--o{ ORDER_STATUS_HISTORY : "logs"
    SALES_ORDER_ITEM ||--o{ SALES_RETURN : "returned"
    SETTLEMENT_PERIOD ||--o{ SETTLEMENT : "includes"
    SETTLEMENT_PERIOD ||--o{ SETTLEMENT_RUN_LOG : "logs"
    SETTLEMENT ||--o{ SETTLEMENT_DETAIL : "breaks down"

    PARTNER {
        bigint id PK
        varchar code UK
        varchar name
        varchar grade_code FK
        varchar status
    }
    PARTNER_GRADE {
        varchar grade_code PK
        varchar name
    }
    REBATE_POLICY {
        bigint id PK
        varchar grade_code FK
        numeric rebate_rate
        date valid_from
        date valid_to
    }
    APP_USER {
        bigint id PK
        varchar login_id UK
        varchar password_hash
        varchar name
        varchar role
        bigint partner_id FK
    }
    PRODUCT {
        bigint id PK
        varchar sku UK
        varchar name
        varchar category
        numeric unit_price
        int stock_qty
        varchar status
    }
    SALES_ORDER {
        bigint id PK
        varchar order_no UK
        bigint partner_id FK
        varchar partner_grade_code
        varchar status
        numeric total_amount
        timestamp ordered_at
        timestamp approved_at
        timestamp shipped_at
        int version
    }
    SALES_ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        int line_no
        bigint product_id FK
        int qty
        numeric unit_price
        numeric amount
    }
    ORDER_STATUS_HISTORY {
        bigint id PK
        bigint order_id FK
        varchar from_status
        varchar to_status
        varchar reason
        bigint changed_by
        timestamp changed_at
    }
    SALES_RETURN {
        bigint id PK
        bigint order_item_id FK
        int qty
        numeric amount
        varchar reason
        timestamp returned_at
    }
    SETTLEMENT_PERIOD {
        char yyyymm PK
        varchar status
        timestamp closed_at
        bigint closed_by
    }
    SETTLEMENT {
        char yyyymm PK
        bigint partner_id PK
        numeric ship_amount
        numeric return_amount
        numeric net_amount
        numeric rebate_amount
        timestamp created_at
    }
    SETTLEMENT_DETAIL {
        bigint id PK
        char yyyymm FK
        bigint partner_id FK
        varchar source_type
        bigint source_id
        numeric amount
        numeric rebate_rate
        numeric rebate_amount
    }
    SETTLEMENT_RUN_LOG {
        bigint id PK
        char yyyymm FK
        varchar run_type
        int row_count
        int elapsed_ms
        varchar result
        bigint run_by
        timestamp run_at
    }
```

### 테이블 메모

| 테이블 | 메모 |
|---|---|
| `rebate_policy` | `valid_to`는 포함하지 않는 날짜이고, NULL이면 지금 적용 중이다. 같은 등급의 기간이 겹치는 것은 `EXCLUDE` 제약(btree_gist)으로 DB에서 막는다 |
| `sales_order` | `version`으로 낙관적 잠금. 두 관리자가 같은 주문을 동시에 처리하는 경우를 막는다 |
| `sales_return` | `amount = 수량 × 원래 주문 단가`. 반품일이 속한 달이 마감됐으면 등록할 수 없다 |
| `settlement_detail` | `source_type`은 `SHIP`(출고 줄) 또는 `RETURN`(반품). 반품은 금액·리베이트 모두 음수다. **[구현]** 프로시저만 만드는 파생 데이터라 성능 때문에 외래키를 뺐다 ([ADR-004](adr/004-settlement-detail-without-fk.md)) |
| `settlement_run_log` | `run_type`은 `PREVIEW` 또는 `CLOSE`. 처리 건수와 소요 시간을 남겨서 성능 비교에 쓴다. **[구현]** 성공한 실행만 남긴다 ([ADR-005](adr/005-no-requires-new-failure-log.md)) |

### 인덱스

처음에는 아래 인덱스를 측정한 뒤 확정하기로 했다.

- `sales_order (shipped_at) WHERE status = 'SHIPPED'`
- `sales_return (returned_at)`
- `rebate_policy (grade_code, valid_from)`
- `settlement_detail (yyyymm, partner_id)`

**[구현]** 네 개 모두 넣었다. 측정해 보니 6개월치 데이터에서는 앞의 세 인덱스로 정산이 빨라지지 않았다. 이력이 쌓일수록 효과가 커질 것으로 보고 지우지는 않았다 (README "성능" 참고).

---

## 5. 정산 프로시저

`run_settlement(p_yyyymm, p_user_id, p_close)` 하나로 미리보기와 마감을 같이 처리한다.

1. 정산월 행을 만들거나(`ON CONFLICT DO NOTHING`) 잠근다(`FOR UPDATE`).
2. 이미 마감된 달이면 중단한다.
3. 그 달의 기존 상세·합계를 지운다.
4. 출고분과 반품분을 `INSERT ... SELECT` 한 번으로 상세에 넣는다. 리베이트율은 출고일 기준으로 조인해서 찾는다.
5. 상세를 거래처별로 합쳐 합계에 넣는다.
6. `p_close`가 참이면 정산월을 `CLOSED`로 바꾼다.
7. 실행 이력에 처리 건수와 소요 시간을 남긴다.

**[구현]** 3번 앞에 "적용할 리베이트 정책이 없는 주문 검사"를 넣었다. 실제 코드는 `V4__settlement_procedure.sql`에 있다.

### 호출할 때 주의할 것

- 프로시저 안에서 `COMMIT`하지 않는다. 트랜잭션은 Spring `@Transactional`이 관리한다.
- MyBatis에서는 JDBC escape 문법(`{call ...}`) 대신 `CALL run_settlement(...)`를 그대로 실행한다. PgJDBC는 기본 설정에서 `{call}`을 함수 호출로 바꾸기 때문에 프로시저 호출이 실패할 수 있다.
- 실패 기록은 처음에 별도 트랜잭션(`REQUIRES_NEW`)으로 남기려고 했다. **[구현]** 바깥 트랜잭션이 잡은 정산월 행 때문에 서로 기다리다 멈추는 경우가 있어서 애플리케이션 로그로 바꿨다 ([ADR-005](adr/005-no-requires-new-failure-log.md)).

---

## 6. 동시성 처리

| 상황 | 처리 방법 |
|---|---|
| 같은 상품 주문을 동시에 승인 | `stock_qty >= 수량` 조건을 건 UPDATE로 재고를 줄이고, 영향받은 행이 0이면 재고 부족으로 승인 실패 |
| 같은 주문을 두 관리자가 동시에 처리 | 상태와 `version`을 조건으로 건 UPDATE. 충돌하면 "다른 사용자가 먼저 처리했다"로 응답 |
| 같은 달을 동시에 마감 | 프로시저에서 정산월 행을 `FOR UPDATE`로 잠근다 |
| 마감한 달의 데이터 변경 | 서비스에서 먼저 확인하고, 트리거로 한 번 더 막는다 |
| 같은 주문 줄을 동시에 반품 | **[구현]** 주문 줄을 `FOR UPDATE`로 잠근 뒤 반품 합계를 다음 문장에서 따로 구한다 ([ADR-007](adr/007-return-qty-check.md)) |

**[구현]** 승인과 승인 취소는 둘 다 "주문 행 → 상품 행(ID 오름차순)" 순서로 잠근다 ([ADR-006](adr/006-approval-locking.md)).

처음에는 k6로 "재고 100개에 승인 200건"만 확인하려고 했다. **[구현]** 같은 상황을 JUnit 동시성 테스트(`ConcurrencyIntegrationTest`)로도 만들어서 CI에서 매번 확인한다. 동시 승인 외에 같은 주문 중복 승인, 승인과 취소 경합, 동시 반품도 넣었다.

---

## 7. API

### 인증

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/auth/csrf` | CSRF 쿠키 받기 |
| POST | `/api/auth/login` | 로그인 (세션) |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/auth/me` | 내 정보와 권한 |

### 마켓플레이스 (거래처)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/market/products` | 판매 중인 상품 목록 (분류, 검색어, 페이지) |
| POST | `/api/market/orders` | 주문 등록 |
| GET | `/api/market/orders` | 내 주문 목록 (상태로 거르기) |
| GET | `/api/market/orders/{id}` | 내 주문 상세 |
| POST | `/api/market/orders/{id}/cancel` | 주문 취소 (승인 대기 상태만) |

### 주문 관리 (관리자)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/orders` | 주문 검색 (상태, 기간, 주문번호·거래처명, 페이지) |
| GET | `/api/admin/orders/status-counts` | 상태별 주문 건수 |
| GET | `/api/admin/orders/{id}` | 주문 상세 (상품, 반품, 처리 이력) |
| POST | `/api/admin/orders/{id}/approve` | 승인 |
| POST | `/api/admin/orders/{id}/reject` | 반려 (사유 필수) |
| POST | `/api/admin/orders/{id}/ship` | 출고 |
| POST | `/api/admin/orders/{id}/cancel` | 승인 취소 |
| POST | `/api/admin/orders/{id}/returns` | 반품 등록 |

### 기준정보 (관리자)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET, POST, PUT | `/api/admin/products` | 상품 관리 |
| GET, POST, PUT | `/api/admin/partners` | 거래처 관리 |
| GET | `/api/admin/grades` | 등급별 현재 요율과 변경 예정 요율 |
| GET, POST | `/api/admin/rebate-policies` | 요율 이력 조회, 요율 변경 등록 |

### 정산 (관리자)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/settlements/{yyyymm}` | 정산월 상태, 거래처별 합계, 실행 이력 |
| POST | `/api/admin/settlements/{yyyymm}/preview` | 미리보기 |
| POST | `/api/admin/settlements/{yyyymm}/close` | 마감 |
| GET | `/api/admin/settlements/{yyyymm}/partners/{partnerId}` | 거래처 정산 상세 |
| GET | `/api/admin/settlements/{yyyymm}/csv` | CSV 내려받기 |

**[구현]** 처음에는 엑셀 파일로 내려받으려고 했는데, 엑셀 라이브러리를 추가하지 않고 UTF-8 BOM을 붙인 CSV로 했다. 엑셀에서 바로 열린다. 실행 이력은 별도 API 대신 정산월 조회 응답에 같이 넣었다.

---

## 8. 화면

| 구분 | 경로 | 화면 | 주요 요소 |
|---|---|---|---|
| 공통 | `/login` | 로그인 | 권한에 따라 주문 화면이나 운영 화면으로 이동 |
| 거래처 | `/market/products` | 상품 주문 | 분류, 검색, 수량 입력, 장바구니 담기 |
| 거래처 | `/market/cart` | 장바구니 | 수량 변경, 예상 금액, 주문하기 |
| 거래처 | `/market/orders` | 주문 내역 | 상태별 탭, 상세, 취소 |
| 관리 | `/admin/orders` | 주문 관리 | 상태별 탭(건수), 검색 조건, 목록 |
| 관리 | `/admin/orders/:id` | 주문 상세 | 상품, 반품 내역, 처리 이력, 승인·반려·출고·취소·반품 |
| 관리 | `/admin/products` | 상품 | 목록과 등록·수정 창 |
| 관리 | `/admin/partners` | 거래처 | 등급, 거래 상태 변경 |
| 관리 | `/admin/rebate-policies` | 리베이트 정책 | 등급별 현재 요율, 요율 변경 등록, 변경 이력 |
| 관리 | `/admin/settlements` | 월 정산 | 월 선택, 미리보기·마감, 거래처별 합계, CSV |
| 관리 | `/admin/settlements/:yyyymm/:partnerId` | 정산 상세 | 출고·반품 줄별 금액과 적용 요율 |

**[구현]** 처음에는 목록 화면에 AG Grid를 쓰려고 했다. 필요한 건 정렬된 표와 페이징 정도라서 직접 만든 `DataTable` 컴포넌트로 했다. 검색 조건과 페이지 번호는 URL 쿼리에 넣어서, 새로 고치거나 뒤로 가도 보던 조건이 그대로 남는다.

---

## 9. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 프론트엔드 | React, TypeScript, Vite, React Router, TanStack Query, React Hook Form |
| 백엔드 | Java 21, Spring Boot 3.5, Spring Security, MyBatis |
| 데이터 | PostgreSQL 16, Flyway |
| 테스트 | JUnit 5, Testcontainers(PostgreSQL), k6 |
| 기타 | Docker Compose, GitHub Actions |

**[구현]** 입력 검증에 Zod를 같이 쓰려고 했는데, 설치할 때 의존성 충돌이 나서 React Hook Form 기본 검증만 썼다 ([트러블슈팅 3번](troubleshooting.md#3-react-hook-form-검증-라이브러리-설치-실패)).

---

## 10. 폴더 구조

```
mini-erp/
├─ backend/
│  └─ src/
│     ├─ main/java/.../minierp/
│     │  ├─ auth/          # 로그인, 권한
│     │  ├─ common/        # 예외, 페이징, 시간대
│     │  ├─ master/        # 상품, 거래처, 리베이트 정책
│     │  ├─ market/        # 거래처용 상품 목록
│     │  ├─ order/         # 주문, 상태 전이, 반품 (관리자용은 order/admin)
│     │  └─ settlement/    # 정산 실행, 조회, CSV
│     ├─ main/resources/
│     │  ├─ mapper/        # MyBatis XML
│     │  └─ db/migration/  # V1 스키마, V2 데모 데이터, V3 주문번호, V4 정산 프로시저, V5 상세 외래키 제거
│     └─ test/             # Testcontainers 통합 테스트
├─ frontend/
│  └─ src/
│     ├─ api/              # API 호출과 TanStack Query 훅
│     ├─ components/       # 표, 페이지 이동, 모달, 상태 표시
│     ├─ features/cart/    # 장바구니
│     ├─ pages/
│     │  ├─ market/
│     │  └─ admin/
│     ├─ routes/           # 권한별 라우트 가드
│     └─ types/
├─ db/
│  ├─ seed/                # 지난달 데모 데이터, 성능 측정용 대량 데이터
│  └─ perf/                # 정산 성능 측정 스크립트
├─ load-test/              # k6 시나리오
├─ docs/
└─ docker-compose.yml
```

---

## 11. 테스트와 성능 측정 계획

### 정산 프로시저 테스트

- 출고만 있는 달: 거래처별 합계와 리베이트가 줄별로 버린 뒤 합친 값과 같다
- 월 중간 요율 변경: 바뀌기 전 출고는 이전 요율, 바뀐 뒤 출고는 새 요율
- 지난달 출고분 반품: 반품한 달에 금액과 리베이트가 모두 음수로 들어간다
- 미리보기 두 번: 결과가 쌓이지 않고 같다
- 마감 후 다시 실행: 거부되고 기존 결과는 그대로다
- 마감한 달 반품 등록: 거부된다

**[구현]** 위 항목을 모두 `SettlementIntegrationTest`로 만들었다. 진행 중인 달 마감 거부, 미래 달 미리보기 거부, CSV 내용 확인도 더했다.

### 성능 측정

거래처 200곳, 상품 500개, 한 달 출고 라인 10만 건을 만들어서 정산 시간을 잰다.

| 단계 | 바꾸는 것 |
|---|---|
| 1 | 인덱스 없음 |
| 2 | 출고일·반품일 인덱스 추가 |
| 3 | 리베이트 정책 조인 개선 |

**[구현]** 3단계는 계획과 달라졌다. 2단계에서 빨라지지 않아서 실행 계획을 단계별로 쪼개 보니, 병목은 정책 조인이 아니라 상세 행 10만 건에 대한 외래키 확인이었다. 그래서 3단계를 "상세 테이블 외래키 제거"로 바꿨다. 데이터는 6개월치(60만 줄)로 늘려서 쟀고, 결과는 README "성능"에 있다.

---

## 12. 진행 계획

| 주차 | 목표 | 완료 기준 |
|---|---|---|
| 1주 | 프로젝트 세팅, DB 스키마, 로그인 | Flyway V1 적용, 로그인 화면에서 API 호출 성공 |
| 2주 | 기준정보, 마켓플레이스 주문 | 거래처 계정으로 주문 등록·조회·취소 |
| 3주 | 주문 승인·출고·반품, 정산 프로시저 | 정산 미리보기·마감 화면 동작 |
| 4주 | 테스트 보강, 성능 측정, 동시성 검증 | 측정표 채우기, k6 결과 README 반영 |

**[구현]** 4주차에 GitHub Actions CI를 더했다. 배포는 하지 않았다.

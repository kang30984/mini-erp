# mini-erp

IT 유통사를 가정한 B2B 주문·출고·월 정산 미니 ERP입니다.
거래처가 마켓플레이스에서 주문하면 내부 담당자가 승인·출고하고, 월말에 거래처 등급별 리베이트를 계산해 정산을 마감합니다.

설계 내용은 [docs/DESIGN.md](docs/DESIGN.md)에 정리했습니다.

## 진행 상황

- [x] 1주차: 프로젝트 세팅, DB 스키마, 로그인
- [x] 2주차: 기준정보(상품, 거래처, 리베이트 정책), 마켓플레이스 주문
- [ ] 3주차: 주문 승인·출고·반품, 정산 프로시저
- [ ] 4주차: 테스트 보강, 성능 측정, 동시성 검증

## 기술 스택

| 구분 | 기술 |
|---|---|
| Frontend | React, TypeScript, Vite, React Router, TanStack Query, React Hook Form |
| Backend | Java 21, Spring Boot 3.5, Spring Security, MyBatis |
| Database | PostgreSQL 16, Flyway |
| Test | JUnit 5, Testcontainers |

## 실행 방법

필요한 것: Docker, JDK 21, Node.js 20 이상

```bash
# 1. DB
docker compose up -d

# 2. 백엔드 (http://localhost:8080)
cd backend
./gradlew bootRun     # Windows: gradlew.bat bootRun. 실행 시 Flyway가 스키마와 데모 데이터를 넣는다

# 3. 프론트엔드 (http://localhost:5173)
cd frontend
npm install
npm run dev
```

테스트는 Testcontainers가 PostgreSQL 컨테이너를 직접 띄우기 때문에 Docker만 켜져 있으면 됩니다.

```bash
cd backend
./gradlew test        # Windows: gradlew.bat test
```

## 데모 계정

로컬 개발용 계정입니다. `V2__seed_demo.sql`로 들어갑니다.

| 아이디 | 비밀번호 | 권한 | 소속 |
|---|---|---|---|
| admin | admin1234! | 관리자 | - |
| partner1 | partner1234! | 거래처 | 한빛시스템 (GOLD) |
| partner2 | partner1234! | 거래처 | 새솔정보통신 (SILVER) |

## 구현하면서 신경 쓴 부분

### 로그인 (1주차)

- **세션 + CSRF 쿠키 방식**: SPA가 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 보냅니다. 개발 환경에서는 Vite 프록시로 같은 출처를 만들어 CORS 설정 없이 세션 쿠키를 씁니다.
- **세션 고정 방지**: 로그인에 성공하면 기존 세션 ID를 새로 발급합니다.
- **계정 존재 여부 노출 방지**: 없는 아이디, 틀린 비밀번호, 사용 중지 계정을 모두 같은 메시지로 응답합니다. Spring Security는 비밀번호 확인 전에 계정 상태를 먼저 검사하기 때문에 메시지를 나누면 비밀번호 없이도 계정 상태를 알 수 있습니다.
- **거래처 상태 연동**: 거래처가 INACTIVE가 되면 소속 사용자는 로그인할 수 없습니다. 사용자 테이블에 상태를 따로 두지 않고 조회 시 거래처 상태로 판단합니다.

### 기준정보와 주문 (2주차)

- **가격은 서버가 정한다**: 주문 요청에는 상품 ID와 수량만 받고, 단가는 DB에서 다시 읽어 계산합니다. 요청 본문에 단가를 넣어 보내도 무시됩니다.
- **거래처는 세션에서 꺼낸다**: 주문 등록·조회·취소 모두 요청 값이 아니라 로그인 세션의 거래처 ID로 범위를 제한합니다. 다른 거래처 주문은 403이 아니라 404로 응답해서 주문이 있다는 사실도 알려 주지 않습니다.
- **주문 시점 등급 저장**: 주문에 당시 거래처 등급을 복사해 둡니다. 나중에 등급을 바꿔도 지난 주문의 정산 기준은 그대로입니다.
- **조건부 UPDATE로 취소**: "상태 조회 → 변경"을 나누지 않고 `WHERE status = 'REQUESTED'` 조건을 건 UPDATE 한 번으로 취소합니다. 관리자가 같은 순간에 승인해도 둘 중 하나만 반영됩니다.
- **요율 변경은 내일부터**: 지난 날짜로 요율을 바꾸면 이미 출고된 주문의 정산이 달라지기 때문에 막았습니다. 기존 정책 종료와 새 정책 등록은 한 트랜잭션에서 잠금을 걸고 처리합니다.
- **MyBatis 세션 캐시 주의**: 같은 트랜잭션에서 같은 select를 다시 부르면 MyBatis가 캐시된 결과를 돌려줍니다. 주문번호 시퀀스와 `FOR UPDATE` 조회는 `flushCache="true"`로 캐시를 끄지 않으면 같은 번호가 다시 나오거나 잠금이 안 걸립니다.
- **검색어 이스케이프**: 검색어에 `%`나 `_`를 넣으면 와일드카드가 아니라 글자 그대로 검색되게 처리했습니다.
- **한국 시간 기준**: 서버 위치와 상관없이 날짜 계산이 한국 시간으로 되도록 `Clock` 빈과 DB 세션 시간대를 맞췄습니다.

### DB 제약

- 리베이트 정책은 적용 기간이 겹치면 안 됩니다. 서비스 코드에서만 막으면 동시 등록이나 직접 입력으로 깨질 수 있어서, PostgreSQL `EXCLUDE` 제약(btree_gist)으로 DB에서 막았습니다.
- 거래처 사용자는 반드시 거래처에 소속되고 관리자는 소속이 없도록 `CHECK` 제약을 걸었습니다.

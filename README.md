# mini-erp

IT 유통사를 가정한 B2B 주문·출고·월 정산 미니 ERP입니다.
거래처가 마켓플레이스에서 주문하면 내부 담당자가 승인·출고하고, 월말에 거래처 등급별 리베이트를 계산해 정산을 마감합니다.

설계 내용은 [docs/DESIGN.md](docs/DESIGN.md)에 정리했습니다.

## 진행 상황

- [x] 1주차: 프로젝트 세팅, DB 스키마, 로그인
- [ ] 2주차: 기준정보, 마켓플레이스 주문
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
./gradlew bootRun     # Windows: gradlew.bat bootRun

# 3. 프론트엔드 (http://localhost:5173)
cd frontend
npm install
npm run dev
```

테스트는 Testcontainers가 PostgreSQL 컨테이너를 직접 띄우기 때문에 Docker만 켜져 있으면 됩니다.

```bash
cd backend
./gradlew test
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

### DB 제약

- 리베이트 정책은 적용 기간이 겹치면 안 됩니다. 서비스 코드에서만 막으면 동시 등록이나 직접 입력으로 깨질 수 있어서, PostgreSQL `EXCLUDE` 제약(btree_gist)으로 DB에서 막았습니다.
- 거래처 사용자는 반드시 거래처에 소속되고 관리자는 소속이 없도록 `CHECK` 제약을 걸었습니다.

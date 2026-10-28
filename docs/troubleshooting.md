# 트러블슈팅 기록

실행하고 측정하면서 실제로 막혔던 문제들이다. 1~4번은 대부분 코드 자체보다는 도구 버전이나 기본 설정에서 생긴 문제였고, 5·6번은 측정 결과를 잘못 읽을 뻔한 경우다. 7번은 구현을 마친 뒤 코드를 다시 읽으면서 찾은 정합성 문제다.

## 1. 최신 Docker Desktop에서 Testcontainers가 Docker를 찾지 못함

Docker Desktop을 켜 둔 상태에서도 통합 테스트가 시작하자마자 `ContainerFetchException`으로 실패했다. 예외를 따라가면 `DockerClientProviderStrategy`에서 쓸 수 있는 Docker 환경을 찾지 못했다는 내용이었다.

최신 Docker Desktop에 들어 있는 Docker Engine 29부터 오래된 API 버전으로 들어오는 요청을 받지 않는데, Spring Boot 3.5.6이 관리하는 Testcontainers 1.21.3이 그 오래된 버전으로 접속한다. Spring Boot가 정해 주는 버전 중 Testcontainers만 1.21.4로 올리니 테스트가 통과했다.

```gradle
ext['testcontainers.version'] = '1.21.4'
```

Spring Boot를 올리면 이 줄은 필요 없어질 수 있어서, `build.gradle`에 이유를 주석으로 남겨 뒀다.

## 2. Windows에 Gradle이 없어서 wrapper를 만들 수 없음

처음에 `gradlew` 파일 없이 `build.gradle`만 만들어 두고, 각자 `gradle wrapper`로 만들게 했다. Windows PC에 Gradle이 설치돼 있지 않아서 `'gradle'은(는) 내부 또는 외부 명령... 이 아닙니다`가 나왔다.

Gradle 공식 저장소의 8.14.3 태그에서 `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`를 받아 저장소에 같이 넣었다. 이제는 JDK만 있으면 `gradlew.bat test`로 바로 실행된다.

Windows에서 커밋하면 `gradlew`의 줄바꿈이 CRLF로 바뀌어 Linux CI에서 실행되지 않을 수 있다. `.gitattributes`에서 `gradlew`는 LF, `.bat`는 CRLF로 고정했고, CI에서는 실행 권한이 빠졌을 때를 대비해 `chmod +x gradlew`를 먼저 실행한다.

## 3. React Hook Form 검증 라이브러리 설치 실패

입력 검증에 Zod를 쓰려고 `@hookform/resolvers`를 설치했는데 `ERESOLVE unable to resolve dependency tree`로 실패했다. 최신 `@hookform/resolvers`가 여러 검증 라이브러리를 선택적 의존성으로 걸어 두고 있었고, 그중 하나의 버전 요구가 서로 맞지 않았다.

`--legacy-peer-deps`로 무시하고 설치할 수도 있었지만, 그러면 나중에 실제로 버전이 안 맞을 때도 경고 없이 넘어간다. 로그인과 기준정보 입력 폼은 필수 입력, 형식, 범위 정도만 확인하면 돼서 React Hook Form 기본 검증(`required`, `pattern`, `validate`)으로 바꿨다. 같은 검증을 서버에서 Bean Validation으로 한 번 더 하기 때문에 화면 검증은 사용자 편의용이다.

## 4. React Router 8에서 RouterProvider를 찾지 못함

React Router를 설치하니 8 버전이 깔렸다. 7 버전 문서대로 `react-router`에서 `RouterProvider`를 가져오려고 하니 타입 검사에서 실패했다.

패키지의 `exports`를 열어 보니 DOM 전용 기능은 `react-router/dom`으로 나뉘어 있었다. `RouterProvider`만 `react-router/dom`에서 가져오고, 나머지(`createBrowserRouter`, `Navigate`, `useNavigate` 등)는 그대로 `react-router`에서 가져온다.

## 5. k6 부하 테스트가 두 번째 요청부터 403

k6 스크립트를 처음 만들고 돌렸을 때, 가상 사용자마다 첫 승인만 성공하고 나머지는 전부 403이 났다. 40건 중 30건이 실패로 집계됐다.

로그인은 가상 사용자마다 한 번만 하고 토큰을 변수에 담아 재사용했는데, k6는 기본적으로 반복(iteration)이 끝날 때마다 쿠키를 지운다. 두 번째 반복부터는 세션 쿠키와 CSRF 쿠키가 없는 요청이 나가고 있었다.

옵션에 `noCookiesReset: true`를 넣어서 해결했다. 409(재고 부족)는 이 테스트에서 정상 응답이라 `http.expectedStatuses`로 성공에 포함시켰고, 그 밖의 오류가 하나라도 있으면 실패하도록 임계값을 걸었다.

## 6. k6 응답 시간이 모든 요청을 섞은 값이었음

처음 측정 결과는 응답 시간 p95가 2.97초였다. 승인 처리가 그렇게 느릴 이유가 없어서 결과를 다시 보니, 711건의 요청 안에 승인 200건뿐 아니라 준비 단계의 주문 등록 200건, 가상 사용자 50명의 로그인, 마지막 확인용 조회 200건이 다 들어 있었다.

요청마다 `name` 태그(`approve`, `login`)를 붙이고 태그별 임계값을 걸어서, 결과에 요청 종류별 시간이 따로 나오게 했다. 다시 재 보니 승인은 중앙값 733ms, p95 1.08s였고 로그인은 중앙값 477ms였다. 승인이 느린 이유는 같은 상품 재고 행에 줄을 서기 때문이고, 로그인은 BCrypt 확인이 한꺼번에 몰렸기 때문이다 ([ADR-006](adr/006-approval-locking.md)).

하나로 뭉친 숫자만 봤다면 "승인이 3초 걸린다"고 잘못 적을 뻔했다.

## 7. 코드 리뷰에서 다시 찾은 정합성 문제 다섯 가지

구현을 마친 뒤 코드를 다시 읽으면서, 테스트는 통과하지만 실제로는 틀릴 수 있는 곳을 다섯 군데 찾았다. 테스트가 "내가 상상한 경로"만 확인하고 있었다는 게 공통점이다. 각각 재현부터 한 뒤 고쳤다.

| 문제 | 재현 | 해결 |
|---|---|---|
| 상품 수정이 승인과 겹치면 재고가 되살아남 | 화면을 연 뒤 재고를 줄이고 예전 숫자로 저장 | 재고 비교 후 교체 ([ADR-010](adr/010-product-stock-compare-and-set.md)) |
| 마감월 트리거가 주문 INSERT·DELETE, 주문 줄, 리베이트 정책을 막지 않음 | 마감 후 SQL로 주문 줄 금액 변경 | 정산이 읽는 데이터 기준으로 트리거 확대, V6 ([ADR-003](adr/003-closing-rules.md)) |
| 월 경계에서 마감과 출고가 겹치면 정산에서 빠진 출고가 남음 | 로컬 PostgreSQL에서 두 세션을 겹쳐 양방향 재현 | 정산월 advisory lock, V7 ([ADR-009](adr/009-settlement-month-lock.md)) |
| 거래 중지 계정은 비밀번호 비교 없이 실패해 응답 시간으로 상태가 드러남 | 비밀번호 비교 횟수 확인 | 계정 상태 검사를 비밀번호 확인 뒤로 ([ADR-008](adr/008-session-and-csrf.md)) |
| 한 줄을 나눠 전량 반품해도 리베이트 끝전이 남음 | 389,000원 × 3개, 3.33%, 1개씩 반품 → 2원 남음 | 누적 수량 비율로 배분, V8 ([ADR-002](adr/002-rebate-rate-and-returns.md)) |

월 경계 문제는 단위 테스트나 일반 통합 테스트로는 드러나지 않는다. 테스트가 트랜잭션 하나 안에서 순서대로 돌기 때문이다. 두 트랜잭션을 실제로 겹쳐야 보여서, 커넥션을 두 개 열고 한쪽을 커밋하지 않은 채 다른 쪽을 실행하는 테스트(`SettlementMonthBoundaryTest`)를 따로 만들었다.

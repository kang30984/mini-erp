# 부하 테스트 (k6)

## 시나리오

`approve-race.js` 하나다. 재고가 적은 상품에 주문을 많이 넣어 두고, 관리자 여러 명이 동시에 승인한다.

| 단계 | 하는 일 |
|---|---|
| setup | 관리자로 테스트 상품 등록(재고 `STOCK`개), 거래처로 1개짜리 주문 `ORDERS`건 등록 |
| 본 실행 | 가상 사용자 `VUS`명이 주문을 하나씩 나눠 맡아 동시에 승인 |
| teardown | 남은 재고와 승인된 주문 수를 다시 조회해서 확인 |

통과 조건은 승인된 주문 = 처음 재고, 남은 재고 = 0, 200·409 말고 다른 응답 0건이다.

## 실행

백엔드를 띄운 상태에서 `mini-erp` 폴더에서 실행한다.

```bash
k6 run load-test/approve-race.js
k6 run -e ORDERS=500 -e STOCK=200 -e VUS=100 load-test/approve-race.js   # 규모 조절
```

k6를 설치하지 않았으면 Docker로 돌릴 수 있다. 컨테이너 안에서는 `localhost`가 컨테이너 자신이라 `host.docker.internal`로 PC의 백엔드에 접속한다.

```bash
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < load-test/approve-race.js
```

테스트용 상품과 주문은 DB에 그대로 남는다. 데모 DB에서 돌렸다면 `docker compose down -v`로 정리한다.

## 측정 시 주의

- k6는 기본적으로 반복마다 쿠키를 지운다. 로그인을 가상 사용자마다 한 번만 하기 때문에 `noCookiesReset: true`를 넣었다. 이게 없으면 두 번째 승인부터 403이 난다.
- 409(재고 부족)는 이 테스트에서 기대하는 응답이라 `http.expectedStatuses`로 실패에서 뺐다.
- 전체 `http_req_duration`에는 setup의 주문 등록, 로그인, teardown 조회가 섞여 있다. 승인 시간은 `{ name:approve }` 줄을 본다.

## 결과 기록

측정 결과와 분석은 루트 README의 "성능" 절에 있다.

// ============================================================
// 동시 승인 부하 테스트 (k6)
//
// 재고 STOCK개인 상품에 1개짜리 주문 ORDERS건을 넣고, 관리자 VUS명이 동시에 승인한다.
// 기대 결과: 승인 성공 = STOCK건, 나머지는 409(OUT_OF_STOCK), 남은 재고 = 0
//
// 실행 (백엔드를 띄운 상태에서, mini-erp 폴더):
//   k6 run load-test/approve-race.js
//   k6 run -e ORDERS=500 -e STOCK=200 -e VUS=100 load-test/approve-race.js
//
// 테스트용 상품과 주문이 실제 DB에 남는다. 데모 DB에서 돌린 뒤 정리하려면 DB를 초기화한다.
// ============================================================
import http from 'k6/http'
import { check, fail } from 'k6'
import { Counter } from 'k6/metrics'
import exec from 'k6/execution'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const ORDERS = Number(__ENV.ORDERS || 200)
const STOCK = Number(__ENV.STOCK || 100)
const VUS = Number(__ENV.VUS || 50)
const ADMIN = { loginId: 'admin', password: __ENV.ADMIN_PASSWORD || 'admin1234!' }
const PARTNER = { loginId: 'partner1', password: __ENV.PARTNER_PASSWORD || 'partner1234!' }

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409))

const approved = new Counter('orders_approved')
const outOfStock = new Counter('orders_out_of_stock')
const unexpected = new Counter('orders_unexpected')

export const options = {
  setupTimeout: '2m',
  // 기본값은 반복(iteration)마다 쿠키를 지운다. 그러면 세션과 CSRF 쿠키가 사라져서 두 번째 승인부터 403이 난다
  noCookiesReset: true,
  scenarios: {
    approve: { executor: 'shared-iterations', vus: VUS, iterations: ORDERS, maxDuration: '2m' },
  },
  thresholds: {
    // 200도 409도 아닌 응답이 하나라도 있으면 실패
    orders_unexpected: ['count==0'],
    // 409(재고 부족)는 정상 응답으로 본다. 그 밖의 4xx/5xx가 있으면 실패
    http_req_failed: ['rate==0'],
    // 요청 종류별 응답 시간을 결과에 따로 보이게 한다 (max>=0은 항상 통과하는 표시용 조건)
    'http_req_duration{name:approve}': ['max>=0'],
    'http_req_duration{name:login}': ['max>=0'],
  },
}

/** 세션 쿠키는 VU마다 따로 관리된다. CSRF 쿠키를 받고 로그인한 뒤 토큰을 돌려준다 */
function login(user) {
  http.get(`${BASE}/api/auth/csrf`, { tags: { name: 'login' } })
  const token = http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'][0]
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    tags: { name: 'login' },
  })
  if (res.status !== 200) fail(`${user.loginId} 로그인 실패: ${res.status}`)
  return token
}

function post(token, path, body, name = 'other') {
  return http.post(`${BASE}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    tags: { name },
  })
}

function logout(token) {
  post(token, '/api/auth/logout', {})
}

export function setup() {
  const sku = `K6-${Date.now()}`
  let token = login(ADMIN)
  const product = post(token, '/api/admin/products', {
    sku, name: 'k6 동시 승인 테스트 상품', category: 'NETWORK', unitPrice: 10000, stockQty: STOCK, status: 'ON_SALE',
  })
  if (product.status !== 201) fail(`상품 등록 실패: ${product.status} ${product.body}`)
  const productId = product.json('id')
  logout(token)

  token = login(PARTNER)
  const orderIds = []
  for (let i = 0; i < ORDERS; i++) {
    const res = post(token, '/api/market/orders', { items: [{ productId, qty: 1 }] })
    if (res.status !== 201) fail(`주문 등록 실패: ${res.status} ${res.body}`)
    orderIds.push(res.json('id'))
  }
  logout(token)
  return { sku, productId, orderIds }
}

// VU마다 한 번만 로그인하고 같은 세션을 계속 쓴다
let adminToken = null

export default function (data) {
  if (!adminToken) adminToken = login(ADMIN)
  const orderId = data.orderIds[exec.scenario.iterationInTest]
  const res = post(adminToken, `/api/admin/orders/${orderId}/approve`, { version: 0 }, 'approve')

  if (res.status === 200) approved.add(1)
  else if (res.status === 409 && res.json('code') === 'OUT_OF_STOCK') outOfStock.add(1)
  else unexpected.add(1)

  check(res, { '200 또는 409(OUT_OF_STOCK)': (r) => r.status === 200 || r.status === 409 })
}

export function teardown(data) {
  const token = login(ADMIN)
  const res = http.get(`${BASE}/api/admin/products?keyword=${data.sku}`)
  const stock = res.json('items.0.stockQty')

  let approvedOrders = 0
  for (const id of data.orderIds) {
    if (http.get(`${BASE}/api/admin/orders/${id}`).json('status') === 'APPROVED') approvedOrders++
  }
  logout(token)

  console.log(`\n[결과] 주문 ${ORDERS}건, 재고 ${STOCK}개 → 승인된 주문 ${approvedOrders}건, 남은 재고 ${stock}개`)
  check(null, {
    '승인 건수 = 처음 재고': () => approvedOrders === STOCK,
    '남은 재고 = 0': () => stock === 0,
  })
}

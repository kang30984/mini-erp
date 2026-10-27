-- ============================================================
-- 성능 측정용 대량 데이터
--   거래처 200곳, 상품 500개
--   2026년 1월~6월, 매달 주문 50,000건 x 2줄 = 출고 라인 100,000건 (총 600,000건)
--   출고 라인의 1%는 출고 3일 뒤 반품
--
-- 실행 (mini-erp 폴더, cmd 기준):
--   docker exec -i minierp-postgres psql -U minierp -d minierp < db\seed\perf_seed.sql
-- PC 사양에 따라 1~3분 정도 걸린다. 한 번만 실행할 것 (P1000 거래처가 이미 있으면 중단).
-- ============================================================
\set ON_ERROR_STOP on
SET TIME ZONE 'Asia/Seoul';
\timing on

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM partner WHERE code = 'P1000') THEN
        RAISE EXCEPTION '성능 측정 데이터가 이미 있습니다. 다시 넣으려면 DB를 초기화하세요.';
    END IF;
END;
$$;

-- 같은 결과를 다시 만들 수 있도록 난수 시드를 고정한다
SELECT setseed(0.42);

-- 거래처 200곳 (등급 고르게)
INSERT INTO partner (code, name, grade_code)
SELECT 'P' || (1000 + g),
       '성능거래처' || lpad(g::text, 3, '0'),
       (ARRAY['SILVER', 'GOLD', 'PLATINUM'])[1 + g % 3]
  FROM generate_series(0, 199) g;

-- 상품 500개 (단가 1만~300만 원, 천 원 단위)
INSERT INTO product (sku, name, category, unit_price, stock_qty)
SELECT 'PF-' || lpad(g::text, 4, '0'),
       '성능상품 ' || g,
       (ARRAY['NOTEBOOK', 'DESKTOP', 'MONITOR', 'SERVER', 'STORAGE', 'NETWORK', 'SOFTWARE'])[1 + g % 7],
       (10 + floor(random() * 2990))::int * 1000,
       100000
  FROM generate_series(1, 500) g;

-- 주문: 한 달에 50,000건씩 6개월. 출고 시각은 그 달 안에서 고르게 흩어 놓는다
CREATE TEMP TABLE perf_order AS
SELECT m::date AS month_start,
       g       AS seq,
       m + (floor(random() * extract(day FROM (m + INTERVAL '1 month' - INTERVAL '1 day')))::int) * INTERVAL '1 day'
         + TIME '08:00' + random() * INTERVAL '10 hours' AS shipped_at,
       1000 + floor(random() * 200)::int AS partner_no
  FROM generate_series(DATE '2026-01-01', DATE '2026-06-01', INTERVAL '1 month') m
 CROSS JOIN generate_series(1, 50000) g;

INSERT INTO sales_order (order_no, partner_id, partner_grade_code, status, total_amount,
                         ordered_at, approved_at, shipped_at)
SELECT 'PF' || to_char(po.month_start, 'YYYYMM') || '-' || lpad(po.seq::text, 6, '0'),
       p.id, p.grade_code, 'SHIPPED', 0,
       po.shipped_at - INTERVAL '1 day', po.shipped_at - INTERVAL '12 hours', po.shipped_at
  FROM perf_order po
  JOIN partner p ON p.code = 'P' || po.partner_no;

-- 주문마다 2줄
INSERT INTO sales_order_item (order_id, line_no, product_id, qty, unit_price, amount)
SELECT o.id, l.line_no, pr.id, l.qty, pr.unit_price, pr.unit_price * l.qty
  FROM sales_order o
 CROSS JOIN LATERAL (
        SELECT line_no,
               1 + floor(random() * 5)::int AS qty,
               1 + floor(random() * 500)::int AS product_no
          FROM generate_series(1, 2) line_no
       ) l
  JOIN product pr ON pr.sku = 'PF-' || lpad(l.product_no::text, 4, '0')
 WHERE o.order_no LIKE 'PF%';

UPDATE sales_order o
   SET total_amount = x.total
  FROM (SELECT order_id, SUM(amount) AS total FROM sales_order_item GROUP BY order_id) x
 WHERE x.order_id = o.id
   AND o.order_no LIKE 'PF%';

-- 출고 라인의 1%를 출고 3일 뒤 1개씩 반품
INSERT INTO sales_return (order_item_id, qty, amount, reason, returned_at, created_by)
SELECT i.id, 1, i.unit_price, '성능 측정용 반품', o.shipped_at + INTERVAL '3 days',
       (SELECT id FROM app_user WHERE login_id = 'admin')
  FROM sales_order_item i
  JOIN sales_order o ON o.id = i.order_id
 WHERE o.order_no LIKE 'PF%'
   AND random() < 0.01;

DROP TABLE perf_order;

-- 통계 갱신. 대량으로 넣은 직후에는 플래너가 잘못된 실행 계획을 고를 수 있다
ANALYZE;

SELECT to_char(shipped_at, 'YYYY-MM') AS month, count(*) AS orders
  FROM sales_order WHERE order_no LIKE 'PF%'
 GROUP BY 1 ORDER BY 1;

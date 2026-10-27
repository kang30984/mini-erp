-- ============================================================
-- 정산 프로시저 성능 측정
-- 먼저 db/seed/perf_seed.sql로 대량 데이터를 넣은 뒤 실행한다.
--
-- 실행 (mini-erp 폴더, cmd 기준):
--   docker exec -i minierp-postgres psql -U minierp -d minierp < db\perf\measure_settlement.sql
--
-- 단계마다 트랜잭션을 열고 구조를 바꾼 뒤 정산을 실행하고 ROLLBACK한다.
-- 실제 DB 구조와 데이터는 바뀌지 않는다.
-- 각 단계를 3번씩 실행하니 "CALL" 다음 줄에 찍히는 Time 값 3개 중 가운데 값을 기록한다.
-- ============================================================
SET TIME ZONE 'Asia/Seoul';
\set month '202603'
\timing on

\echo
\echo ---------- 준비: 캐시 데우기 ----------
BEGIN; CALL run_settlement(:'month', 1, false); ROLLBACK;

\echo
\echo ---------- [1] 정산 인덱스 없음 + 상세 외래키 있음 (V4 이전 구조) ----------
BEGIN;
DROP INDEX idx_sales_order_shipped, idx_sales_return_returned, idx_rebate_policy_grade_from;
ALTER TABLE settlement_detail
    ADD CONSTRAINT m_fk_period  FOREIGN KEY (yyyymm)     REFERENCES settlement_period (yyyymm),
    ADD CONSTRAINT m_fk_partner FOREIGN KEY (partner_id) REFERENCES partner (id);
SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK;

\echo
\echo ---------- [2] 정산 인덱스 있음 + 상세 외래키 있음 (V4 구조) ----------
BEGIN;
ALTER TABLE settlement_detail
    ADD CONSTRAINT m_fk_period  FOREIGN KEY (yyyymm)     REFERENCES settlement_period (yyyymm),
    ADD CONSTRAINT m_fk_partner FOREIGN KEY (partner_id) REFERENCES partner (id);
SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK;

\echo
\echo ---------- [3] 정산 인덱스 있음 + 상세 외래키 없음 (V5, 현재 구조) ----------
BEGIN;
SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK TO SAVEPOINT s;
CALL run_settlement(:'month', 1, false);
ROLLBACK;

\echo
\echo ---------- 처리 건수 ----------
SELECT count(*) AS ship_lines
  FROM sales_order o JOIN sales_order_item i ON i.order_id = o.id
 WHERE o.status = 'SHIPPED'
   AND o.shipped_at >= to_date(:'month' || '01', 'YYYYMMDD')
   AND o.shipped_at <  to_date(:'month' || '01', 'YYYYMMDD') + INTERVAL '1 month';

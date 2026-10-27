-- ============================================================
-- 월 정산 프로시저와 마감월 보호 트리거
-- ============================================================

-- 정산 집계용 인덱스: 해당 월 출고분과 반품분을 범위로 찾는다
CREATE INDEX idx_sales_order_shipped ON sales_order (shipped_at) WHERE status = 'SHIPPED';
CREATE INDEX idx_sales_return_returned ON sales_return (returned_at);
CREATE INDEX idx_rebate_policy_grade_from ON rebate_policy (grade_code, valid_from);

-- ------------------------------------------------------------
-- run_settlement: 정산 미리보기(p_close = false) / 마감(p_close = true)
--
-- 1. 정산월 행을 만들거나 잠근다. 같은 월을 동시에 실행하면 뒤 요청은 앞 요청이 끝날 때까지 기다린다.
-- 2. 이미 마감된 월이면 중단한다.
-- 3. 해당 월의 기존 결과를 지우고 다시 만든다. (미리보기를 여러 번 해도 결과가 쌓이지 않음)
-- 4. 출고분은 출고일 기준 월, 반품분은 반품일 기준 월에 넣는다.
--    리베이트율은 둘 다 "원래 출고일"에 적용 중이던 요율을 쓰고, 라인별로 원 미만을 버린다.
-- 5. 트랜잭션 제어(COMMIT)는 하지 않는다. 호출한 쪽(Spring @Transactional)이 관리한다.
-- ------------------------------------------------------------
CREATE OR REPLACE PROCEDURE run_settlement(
    p_yyyymm  VARCHAR,
    p_user_id BIGINT,
    p_close   BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_from    DATE := to_date(p_yyyymm || '01', 'YYYYMMDD');
    v_to      DATE := (to_date(p_yyyymm || '01', 'YYYYMMDD') + INTERVAL '1 month')::date;
    v_status  VARCHAR(10);
    v_missing VARCHAR(20);
    v_rows    INT;
    v_started TIMESTAMP := clock_timestamp();
BEGIN
    INSERT INTO settlement_period (yyyymm, status)
    VALUES (p_yyyymm, 'OPEN')
    ON CONFLICT (yyyymm) DO NOTHING;

    SELECT status INTO v_status
      FROM settlement_period
     WHERE yyyymm = p_yyyymm
       FOR UPDATE;

    IF v_status = 'CLOSED' THEN
        RAISE EXCEPTION '이미 마감된 정산월입니다: %', p_yyyymm;
    END IF;

    -- 적용할 리베이트 정책이 없는 건이 있으면 조용히 빠지지 않도록 먼저 막는다
    SELECT x.order_no INTO v_missing
      FROM (
            SELECT o.order_no, o.partner_grade_code, o.shipped_at
              FROM sales_order o
             WHERE o.status = 'SHIPPED'
               AND o.shipped_at >= v_from AND o.shipped_at < v_to
            UNION ALL
            SELECT o.order_no, o.partner_grade_code, o.shipped_at
              FROM sales_return r
              JOIN sales_order_item i ON i.id = r.order_item_id
              JOIN sales_order o      ON o.id = i.order_id
             WHERE r.returned_at >= v_from AND r.returned_at < v_to
           ) x
     WHERE NOT EXISTS (
            SELECT 1 FROM rebate_policy rp
             WHERE rp.grade_code = x.partner_grade_code
               AND x.shipped_at >= rp.valid_from
               AND (rp.valid_to IS NULL OR x.shipped_at < rp.valid_to)
           )
     LIMIT 1;

    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION '출고일에 적용할 리베이트 정책이 없는 주문이 있습니다: %', v_missing;
    END IF;

    DELETE FROM settlement_detail WHERE yyyymm = p_yyyymm;
    DELETE FROM settlement        WHERE yyyymm = p_yyyymm;

    INSERT INTO settlement_detail
           (yyyymm, partner_id, source_type, source_id, amount, rebate_rate, rebate_amount)
    SELECT p_yyyymm, o.partner_id, 'SHIP', i.id,
           i.amount, rp.rebate_rate, floor(i.amount * rp.rebate_rate)
      FROM sales_order o
      JOIN sales_order_item i ON i.order_id = o.id
      JOIN rebate_policy rp
        ON rp.grade_code = o.partner_grade_code
       AND o.shipped_at >= rp.valid_from
       AND (rp.valid_to IS NULL OR o.shipped_at < rp.valid_to)
     WHERE o.status = 'SHIPPED'
       AND o.shipped_at >= v_from
       AND o.shipped_at <  v_to
    UNION ALL
    SELECT p_yyyymm, o.partner_id, 'RETURN', r.id,
           -r.amount, rp.rebate_rate, -floor(r.amount * rp.rebate_rate)
      FROM sales_return r
      JOIN sales_order_item i ON i.id = r.order_item_id
      JOIN sales_order o      ON o.id = i.order_id
      JOIN rebate_policy rp
        ON rp.grade_code = o.partner_grade_code
       AND o.shipped_at >= rp.valid_from
       AND (rp.valid_to IS NULL OR o.shipped_at < rp.valid_to)
     WHERE r.returned_at >= v_from
       AND r.returned_at <  v_to;

    GET DIAGNOSTICS v_rows = ROW_COUNT;

    INSERT INTO settlement
           (yyyymm, partner_id, ship_amount, return_amount, net_amount, rebate_amount, created_at)
    SELECT yyyymm, partner_id,
           COALESCE(SUM(amount) FILTER (WHERE source_type = 'SHIP'), 0),
           COALESCE(-SUM(amount) FILTER (WHERE source_type = 'RETURN'), 0),
           SUM(amount),
           SUM(rebate_amount),
           now()
      FROM settlement_detail
     WHERE yyyymm = p_yyyymm
     GROUP BY yyyymm, partner_id;

    IF p_close THEN
        UPDATE settlement_period
           SET status = 'CLOSED', closed_at = now(), closed_by = p_user_id
         WHERE yyyymm = p_yyyymm;
    END IF;

    INSERT INTO settlement_run_log
           (yyyymm, run_type, row_count, elapsed_ms, result, run_by, run_at)
    VALUES (p_yyyymm,
            CASE WHEN p_close THEN 'CLOSE' ELSE 'PREVIEW' END,
            v_rows,
            (EXTRACT(EPOCH FROM clock_timestamp() - v_started) * 1000)::int,
            'SUCCESS', p_user_id, now());
END;
$$;

-- ------------------------------------------------------------
-- 마감월 보호: 마감된 월에 속하는 출고·반품 데이터는 어떤 경로로도 바꿀 수 없다.
-- 서비스 코드에서 먼저 막지만, SQL을 직접 실행하는 경우까지 DB에서 한 번 더 막는다.
-- check_violation(23514)으로 올려서 Spring에서는 DataIntegrityViolationException이 된다.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION assert_month_open(p_at TIMESTAMP)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_at IS NOT NULL AND EXISTS (
        SELECT 1 FROM settlement_period
         WHERE yyyymm = to_char(p_at, 'YYYYMM') AND status = 'CLOSED'
    ) THEN
        RAISE EXCEPTION '마감된 정산월(%)의 데이터는 변경할 수 없습니다.', to_char(p_at, 'YYYYMM')
              USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION guard_sales_return()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM assert_month_open(OLD.returned_at);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM assert_month_open(NEW.returned_at);
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_sales_return_closed_month
BEFORE INSERT OR UPDATE OR DELETE ON sales_return
FOR EACH ROW EXECUTE FUNCTION guard_sales_return();

CREATE OR REPLACE FUNCTION guard_sales_order()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.status, NEW.shipped_at, NEW.total_amount, NEW.partner_grade_code)
       IS DISTINCT FROM
       ROW(OLD.status, OLD.shipped_at, OLD.total_amount, OLD.partner_grade_code) THEN
        PERFORM assert_month_open(OLD.shipped_at);
        PERFORM assert_month_open(NEW.shipped_at);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sales_order_closed_month
BEFORE UPDATE ON sales_order
FOR EACH ROW EXECUTE FUNCTION guard_sales_order();

-- ============================================================
-- 월 경계에서 마감과 출고·반품이 겹칠 때의 경합 방지
--
-- 문제
--   출고일은 shipped_at = now()로 정하는데, PostgreSQL의 now()는 트랜잭션 시작 시각이다.
--   그리고 V4의 assert_month_open은 정산월 상태를 잠금 없이 읽기만 했다.
--   9월 30일 23:59:59에 시작한 출고 트랜잭션이 10월 1일 0시 직후 커밋되는 사이 9월 마감이 끼어들면,
--     - 마감 프로시저는 아직 커밋 전인 출고를 보지 못해 정산에서 빼고
--     - 출고 쪽 트리거는 마감 커밋 전에 검사해서 통과한다
--   결과적으로 마감된 9월에 정산에서 빠진 출고가 남는다. (마감이 먼저 시작해도 같은 결과)
--   반품(returned_at DEFAULT now())도 같은 구조다.
--
-- 해결
--   정산월 단위 advisory lock을 쓴다. 키는 (settlement_lock_class(), yyyymm).
--     - 출고·반품을 바꾸는 쪽(assert_month_open): 해당 월의 공유 잠금을 잡은 뒤 마감 여부를 확인한다
--     - 정산 프로시저: 시작할 때 해당 월의 배타 잠금을 잡는다
--   출고가 먼저면 마감이 출고 커밋을 기다렸다가 새 스냅샷으로 그 출고까지 포함해 계산하고,
--   마감이 먼저면 출고 쪽이 기다렸다가 CLOSED를 보고 거부된다. 다시 시도하면 새 트랜잭션의 now()로 이번 달에 들어간다.
--   공유 잠금끼리는 부딪히지 않으므로 평소 출고·반품끼리는 서로 기다리지 않는다.
--   행 잠금 대신 advisory lock을 쓴 이유: 처음 정산하는 달은 settlement_period 행이 아직 없어서 잠글 행이 없다.
--   잠금은 트랜잭션이 끝나면 자동으로 풀린다(xact 계열).
-- ============================================================

-- advisory lock 첫 번째 키. 다른 용도의 advisory lock과 겹치지 않도록 한곳에서 정한다.
CREATE OR REPLACE FUNCTION settlement_lock_class()
RETURNS int
LANGUAGE sql
IMMUTABLE
AS $$ SELECT 72001 $$;

CREATE OR REPLACE FUNCTION assert_month_open(p_at TIMESTAMP)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_month CHAR(6);
BEGIN
    IF p_at IS NULL THEN
        RETURN;
    END IF;
    v_month := to_char(p_at, 'YYYYMM');

    -- 같은 달 정산(배타 잠금)이 진행 중이면 끝날 때까지 기다린다.
    -- 이 함수는 VOLATILE이라 아래 조회는 잠금을 받은 뒤의 새 스냅샷으로 실행되어, 방금 커밋된 마감도 보인다.
    PERFORM pg_advisory_xact_lock_shared(settlement_lock_class(), v_month::int);

    IF EXISTS (
        SELECT 1 FROM settlement_period
         WHERE yyyymm = v_month AND status = 'CLOSED'
    ) THEN
        RAISE EXCEPTION '마감된 정산월(%)의 데이터는 변경할 수 없습니다.', v_month
              USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

-- ------------------------------------------------------------
-- run_settlement: V4와 같고, 시작할 때 정산월 배타 잠금을 잡는 줄만 추가했다.
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
    -- 정산월 배타 잠금. 같은 달 출고·반품을 바꾸는 트랜잭션(공유 잠금)이 끝날 때까지 기다리고,
    -- 이 트랜잭션이 끝날 때까지 그 달 출고·반품 변경을 기다리게 한다 (V7 머리 주석 참고)
    PERFORM pg_advisory_xact_lock(settlement_lock_class(), p_yyyymm::int);

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

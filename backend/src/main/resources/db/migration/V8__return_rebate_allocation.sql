-- ============================================================
-- 부분 반품의 리베이트 끝전 맞추기
--
-- 문제
--   출고 리베이트는 줄 단위로 floor(줄 금액 × 요율), 반품 리베이트는 반품 건마다 floor(반품 금액 × 요율)로
--   따로 버렸다. 그래서 한 줄을 여러 번 나눠 반품하면 전량 반품해도 리베이트가 남았다.
--   예) 389,000원 × 3개, 요율 3.33%
--       출고 리베이트 floor(1,167,000 × 0.0333) = 38,861
--       1개씩 세 번 반품 floor(389,000 × 0.0333) = 12,953 × 3 = 38,859  → 2원이 거래처에 남음
--
-- 해결
--   반품 리베이트를 원래 줄 리베이트(출고 때 실제로 준 금액)를 누적 반품 수량 비율로 배분해서 되돌린다.
--     되돌릴 금액 = floor(줄 리베이트 × 이번까지 누적 반품 수량 / 주문 수량)
--                - floor(줄 리베이트 × 직전까지 누적 반품 수량 / 주문 수량)
--   누적 반품이 주문 수량에 도달하면 합계가 정확히 줄 리베이트와 같아진다(마지막 반품이 끝전을 가져간다).
--   반품이 여러 달에 걸쳐도 누적은 반품일·ID 순으로 전체 반품을 보고 계산하므로 달마다 결과가 바뀌지 않는다.
--   마감한 달의 반품은 트리거로 바꿀 수 없고 새 반품은 항상 그 뒤에 쌓이므로, 이미 마감한 달의 금액도 흔들리지 않는다.
--
--   한 번에 전량 반품하거나, 끝전이 생기지 않는 금액이면 이전과 결과가 같다.
--   대신 부분 반품의 반품 리베이트는 floor(반품 금액 × 요율)과 1원 단위로 다를 수 있다.
-- ============================================================

-- ------------------------------------------------------------
-- run_settlement: V7과 같고, 반품분(RETURN)의 리베이트 계산만 바꿨다.
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
    -- 정산월 배타 잠금 (V7)
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
    -- 반품 리베이트는 원래 줄 리베이트를 누적 반품 수량 비율로 나눠서 되돌린다 (V8 머리 주석 참고)
    --   되돌릴 금액 = floor(줄 리베이트 × 이번까지 누적 반품 수량 / 주문 수량)
    --              - floor(줄 리베이트 × 직전까지 누적 반품 수량 / 주문 수량)
    -- 누적은 다른 달의 반품까지 포함해 계산하고, 결과는 이번 달 반품분만 넣는다
    SELECT p_yyyymm, o.partner_id, 'RETURN', x.id,
           -x.amount, rp.rebate_rate,
           -( floor(floor(i.amount * rp.rebate_rate) * x.cum_qty / i.qty)
            - floor(floor(i.amount * rp.rebate_rate) * (x.cum_qty - x.qty) / i.qty) )
      FROM (
            SELECT r.id, r.order_item_id, r.qty, r.amount, r.returned_at,
                   SUM(r.qty) OVER (PARTITION BY r.order_item_id ORDER BY r.returned_at, r.id) AS cum_qty
              FROM sales_return r
             WHERE r.order_item_id IN (
                    SELECT rr.order_item_id FROM sales_return rr
                     WHERE rr.returned_at >= v_from AND rr.returned_at < v_to)
           ) x
      JOIN sales_order_item i ON i.id = x.order_item_id
      JOIN sales_order o      ON o.id = i.order_id
      JOIN rebate_policy rp
        ON rp.grade_code = o.partner_grade_code
       AND o.shipped_at >= rp.valid_from
       AND (rp.valid_to IS NULL OR o.shipped_at < rp.valid_to)
     WHERE x.returned_at >= v_from
       AND x.returned_at <  v_to;

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

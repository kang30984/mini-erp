-- ============================================================
-- 마감월 보호 범위 확대
--
-- V4의 트리거는 sales_order의 UPDATE와 sales_return만 막았다. 그런데 정산 프로시저는
--   - 주문(sales_order)의 거래처, 등급, 출고일
--   - 주문 줄(sales_order_item)의 금액
--   - 리베이트 정책(rebate_policy)의 요율과 적용 기간
-- 을 읽는다. SQL을 직접 실행하면 아래 경로로 마감한 달의 정산 근거가 바뀔 수 있었다.
--   1. 마감한 달 출고일을 가진 주문을 INSERT 하거나 DELETE
--   2. 마감한 달에 출고된 주문의 줄 금액·수량을 UPDATE (트리거가 아예 없었음)
--   3. 마감한 달에 적용되던 리베이트 요율이나 적용 기간을 UPDATE
-- 이 마이그레이션은 정산이 읽는 데이터를 기준으로 보호 범위를 다시 맞춘다.
-- 오류 코드는 V4와 같은 check_violation(23514)이다.
-- ============================================================

-- [p_from, p_to) 기간이 마감한 정산월과 하루라도 겹치면 거부한다. p_to가 NULL이면 끝이 없는 기간이다.
CREATE OR REPLACE FUNCTION assert_range_open(p_from DATE, p_to DATE)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_closed CHAR(6);
BEGIN
    SELECT sp.yyyymm INTO v_closed
      FROM settlement_period sp
     WHERE sp.status = 'CLOSED'
       AND to_date(sp.yyyymm || '01', 'YYYYMMDD') < COALESCE(p_to, 'infinity'::date)
       AND (to_date(sp.yyyymm || '01', 'YYYYMMDD') + INTERVAL '1 month')::date > p_from
     ORDER BY sp.yyyymm
     LIMIT 1;

    IF v_closed IS NOT NULL THEN
        RAISE EXCEPTION '마감된 정산월(%)에 적용되는 리베이트 정책은 변경할 수 없습니다.', v_closed
              USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

-- ------------------------------------------------------------
-- 1. sales_order: UPDATE에 더해 INSERT, DELETE도 막는다. 정산이 읽는 거래처(partner_id)도 비교 대상에 넣는다.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION guard_sales_order()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM assert_month_open(NEW.shipped_at);
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        PERFORM assert_month_open(OLD.shipped_at);
        RETURN OLD;
    END IF;

    IF ROW(NEW.status, NEW.shipped_at, NEW.total_amount, NEW.partner_grade_code, NEW.partner_id)
       IS DISTINCT FROM
       ROW(OLD.status, OLD.shipped_at, OLD.total_amount, OLD.partner_grade_code, OLD.partner_id) THEN
        PERFORM assert_month_open(OLD.shipped_at);
        PERFORM assert_month_open(NEW.shipped_at);
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_sales_order_closed_month ON sales_order;
CREATE TRIGGER trg_sales_order_closed_month
BEFORE INSERT OR UPDATE OR DELETE ON sales_order
FOR EACH ROW EXECUTE FUNCTION guard_sales_order();

-- ------------------------------------------------------------
-- 2. sales_order_item: 정산 금액의 원본이다. 소속 주문의 출고일이 마감한 달이면 추가·수정·삭제를 막는다.
--    정산과 상관없는 칼럼만 바뀌는 UPDATE는 통과시킨다.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION guard_sales_order_item()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_changed BOOLEAN := TG_OP <> 'UPDATE'
        OR ROW(NEW.order_id, NEW.product_id, NEW.qty, NEW.unit_price, NEW.amount)
           IS DISTINCT FROM
           ROW(OLD.order_id, OLD.product_id, OLD.qty, OLD.unit_price, OLD.amount);
BEGIN
    IF NOT v_changed THEN
        RETURN NEW;
    END IF;

    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM assert_month_open((SELECT o.shipped_at FROM sales_order o WHERE o.id = OLD.order_id));
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        PERFORM assert_month_open((SELECT o.shipped_at FROM sales_order o WHERE o.id = NEW.order_id));
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_sales_order_item_closed_month
BEFORE INSERT OR UPDATE OR DELETE ON sales_order_item
FOR EACH ROW EXECUTE FUNCTION guard_sales_order_item();

-- ------------------------------------------------------------
-- 3. rebate_policy: 마감한 달에 적용되던 요율이나 기간을 바꾸지 못하게 한다.
--    서비스는 "내일 이후 시작"만 받지만, SQL로 직접 고치는 경우까지 막는다.
--    새 정책을 등록하면서 열린 정책의 종료일을 미래 날짜로 정하는 정상 흐름은
--    바뀐 구간이 미래뿐이라 통과한다.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION guard_rebate_policy()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM assert_range_open(NEW.valid_from, NEW.valid_to);
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        PERFORM assert_range_open(OLD.valid_from, OLD.valid_to);
        RETURN OLD;
    END IF;

    IF ROW(NEW.grade_code, NEW.rebate_rate, NEW.valid_from)
       IS DISTINCT FROM
       ROW(OLD.grade_code, OLD.rebate_rate, OLD.valid_from) THEN
        -- 등급, 요율, 시작일이 바뀌면 이전 기간과 새 기간 전체가 영향을 받는다
        PERFORM assert_range_open(OLD.valid_from, OLD.valid_to);
        PERFORM assert_range_open(NEW.valid_from, NEW.valid_to);
    ELSIF NEW.valid_to IS DISTINCT FROM OLD.valid_to THEN
        -- 종료일만 바뀌면 이전 종료일과 새 종료일 사이 구간만 영향을 받는다 (NULL은 끝없음)
        PERFORM assert_range_open(
            LEAST(COALESCE(OLD.valid_to, 'infinity'::date), COALESCE(NEW.valid_to, 'infinity'::date)),
            GREATEST(COALESCE(OLD.valid_to, 'infinity'::date), COALESCE(NEW.valid_to, 'infinity'::date)));
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rebate_policy_closed_month
BEFORE INSERT OR UPDATE OR DELETE ON rebate_policy
FOR EACH ROW EXECUTE FUNCTION guard_rebate_policy();

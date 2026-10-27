-- ============================================================
-- 지난달 출고 데모 데이터 (선택 실행)
-- 정산 마감은 지난달까지만 할 수 있어서, 화면에서 마감까지 바로 해 보려면 지난달 출고 건이 필요하다.
-- 실행: docker exec -i minierp-postgres psql -U minierp -d minierp < db/seed/demo_last_month.sql
-- 여러 번 실행하면 주문이 그만큼 더 생긴다.
-- ============================================================
SET TIME ZONE 'Asia/Seoul';

DO $$
DECLARE
    v_month_start DATE   := (date_trunc('month', now()) - INTERVAL '1 month')::date;
    v_admin       BIGINT := (SELECT id FROM app_user WHERE login_id = 'admin');
    v_order_id    BIGINT;
    v_shipped_at  TIMESTAMP;
    r             RECORD;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('P0001', 'NB-14-001', 3,  4),
            ('P0001', 'MN-27-QHD', 5,  9),
            ('P0001', 'NW-SW-24',  2, 21),
            ('P0002', 'DT-MINI-01', 4, 7),
            ('P0002', 'MN-24-FHD', 10, 18),
            ('P0003', 'SV-1U-01',  1, 12),
            ('P0003', 'ST-NAS-4B', 2, 25)
        ) AS t(partner_code, sku, qty, ship_day)
    LOOP
        v_shipped_at := v_month_start + (r.ship_day - 1) + TIME '14:00';

        INSERT INTO sales_order (order_no, partner_id, partner_grade_code, status, total_amount,
                                 ordered_at, approved_at, shipped_at)
        SELECT 'SO' || to_char(v_shipped_at - INTERVAL '1 day', 'YYYYMMDD') || '-'
                    || lpad(nextval('sales_order_no_seq')::text, 6, '0'),
               p.id, p.grade_code, 'SHIPPED', pr.unit_price * r.qty,
               v_shipped_at - INTERVAL '1 day', v_shipped_at - INTERVAL '20 hours', v_shipped_at
          FROM partner p, product pr
         WHERE p.code = r.partner_code AND pr.sku = r.sku
        RETURNING id INTO v_order_id;

        INSERT INTO sales_order_item (order_id, line_no, product_id, qty, unit_price, amount)
        SELECT v_order_id, 1, pr.id, r.qty, pr.unit_price, pr.unit_price * r.qty
          FROM product pr WHERE pr.sku = r.sku;

        INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, changed_at)
        VALUES (v_order_id, NULL, 'REQUESTED',
                    -- 소속 사용자가 없는 거래처(P0003)는 관리자가 대신 접수한 것으로 남긴다
                    COALESCE((SELECT u.id FROM app_user u JOIN partner p ON p.id = u.partner_id
                               WHERE p.code = r.partner_code LIMIT 1), v_admin),
                    v_shipped_at - INTERVAL '1 day'),
               (v_order_id, 'REQUESTED', 'APPROVED', v_admin, v_shipped_at - INTERVAL '20 hours'),
               (v_order_id, 'APPROVED', 'SHIPPED', v_admin, v_shipped_at);
    END LOOP;
END;
$$;

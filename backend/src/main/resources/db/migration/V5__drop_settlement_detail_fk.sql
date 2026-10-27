-- ============================================================
-- 정산 상세(settlement_detail)의 외래키 제거
--
-- 측정 결과 (2026년 3월분, 출고·반품 라인 약 10만 건)
--   정산 실행 시간의 절반 이상이 상세 행 10만 건 각각에 대한 외래키 확인(행마다 실행되는 제약 트리거)이었다.
--
-- 제거해도 되는 이유
--   - 상세는 run_settlement 프로시저만 만드는 파생 데이터이고, 실행할 때마다 지우고 다시 만든다.
--   - 원본인 sales_order(partner_id 외래키)와 settlement_period에서만 값을 가져오므로 잘못된 참조가 생길 경로가 없다.
--   - 거래처 합계 테이블(settlement)은 행이 거래처 수만큼(수백 건)이라 외래키를 그대로 둔다.
-- ============================================================
ALTER TABLE settlement_detail
    DROP CONSTRAINT settlement_detail_yyyymm_fkey,
    DROP CONSTRAINT settlement_detail_partner_id_fkey;

COMMENT ON TABLE settlement_detail IS
    'run_settlement 프로시저가 만드는 파생 데이터. 성능 때문에 외래키 없이 프로시저가 정합성을 책임진다 (V5 참고)';

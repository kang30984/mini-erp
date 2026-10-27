-- 주문번호(SO20261027-000001) 뒷자리 일련번호
CREATE SEQUENCE sales_order_no_seq;

-- 상품명·코드 부분 검색, 거래처 검색에 쓰는 기본 정렬 인덱스
CREATE INDEX idx_product_category ON product (category, status);
CREATE INDEX idx_partner_grade    ON partner (grade_code, status);

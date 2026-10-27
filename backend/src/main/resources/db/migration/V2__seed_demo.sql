-- 로컬 개발용 데모 데이터
-- 계정 비밀번호는 README의 "데모 계정" 참고 (BCrypt 해시로 저장)

INSERT INTO partner_grade (grade_code, name) VALUES
    ('SILVER',   '실버'),
    ('GOLD',     '골드'),
    ('PLATINUM', '플래티넘');

INSERT INTO rebate_policy (grade_code, rebate_rate, valid_from, valid_to) VALUES
    ('SILVER',   0.0100, DATE '2026-01-01', NULL),
    ('GOLD',     0.0200, DATE '2026-01-01', NULL),
    ('PLATINUM', 0.0300, DATE '2026-01-01', NULL);

INSERT INTO partner (code, name, grade_code) VALUES
    ('P0001', '한빛시스템',   'GOLD'),
    ('P0002', '새솔정보통신', 'SILVER'),
    ('P0003', '누리아이티',   'PLATINUM');

INSERT INTO app_user (login_id, password_hash, name, role, partner_id) VALUES
    ('admin',    '$2a$10$.73jfMss/V//h/DdpDRtjuJ2uAjdLhTIJaBM0H.6r9svsXqgp7Aai', '운영관리자', 'ADMIN', NULL),
    ('partner1', '$2a$10$duRRXjwlzE8V1ei.nHPrZessc1eLnEGmuAxTJviCq97y5YiPHQq.y', '한빛 구매담당', 'PARTNER',
        (SELECT id FROM partner WHERE code = 'P0001')),
    ('partner2', '$2a$10$duRRXjwlzE8V1ei.nHPrZessc1eLnEGmuAxTJviCq97y5YiPHQq.y', '새솔 구매담당', 'PARTNER',
        (SELECT id FROM partner WHERE code = 'P0002'));

INSERT INTO product (sku, name, category, unit_price, stock_qty) VALUES
    ('NB-14-001',  '업무용 노트북 14형 (i5, 16GB, 512GB)', 'NOTEBOOK', 1290000,  80),
    ('NB-16-002',  '고성능 노트북 16형 (i7, 32GB, 1TB)',   'NOTEBOOK', 2190000,  40),
    ('DT-MINI-01', '미니 PC (Ryzen 5, 16GB, 512GB)',       'DESKTOP',   790000, 120),
    ('MN-27-QHD',  '27형 QHD 모니터',                      'MONITOR',   389000, 200),
    ('MN-24-FHD',  '24형 FHD 모니터',                      'MONITOR',   189000, 300),
    ('SV-1U-01',   '1U 랙 서버 (Xeon, 64GB)',              'SERVER',   6890000,  10),
    ('ST-NAS-4B',  '4베이 NAS',                            'STORAGE',   899000,  35),
    ('NW-SW-24',   '24포트 기가비트 스위치',               'NETWORK',   459000,  60),
    ('NW-AP-6E',   'Wi-Fi 6E 무선 AP',                     'NETWORK',   329000,  90),
    ('SW-OFC-1Y',  '오피스 소프트웨어 1년 라이선스',       'SOFTWARE',  129000, 999);

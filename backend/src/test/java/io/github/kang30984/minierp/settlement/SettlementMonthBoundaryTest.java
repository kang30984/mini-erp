package io.github.kang30984.minierp.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kang30984.minierp.IntegrationTestSupport;
import io.github.kang30984.minierp.common.TimeConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 경계에서 마감과 출고가 겹치는 경우 (V7).
 *
 * 출고일은 트랜잭션 시작 시각(now())이라, 지난달 마지막 순간에 시작한 출고 트랜잭션이 이번 달 초에 커밋될 수 있다.
 * 그 사이 지난달 마감이 끼어들어도 "마감된 달에 정산에서 빠진 출고"가 생기면 안 된다.
 * 두 트랜잭션이 실제로 겹쳐야 하므로 테스트 트랜잭션을 끄고 커넥션을 직접 다룬 뒤, 만든 데이터를 직접 지운다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementMonthBoundaryTest extends IntegrationTestSupport {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    DataSource dataSource;

    private final YearMonth previous = YearMonth.now(TimeConfig.BUSINESS_ZONE).minusMonths(1);
    private final String prev = previous.format(YYYYMM);
    /** 지난달 마지막 순간. 이 시각에 시작한 출고 트랜잭션과 같다 */
    private final LocalDateTime lastMoment = previous.atEndOfMonth().atTime(23, 59, 59);

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private List<Map<String, Object>> originalPolicies;
    private long adminId;
    private long orderId;

    @BeforeEach
    void setUp() {
        // 데모 정책 시작일(2026-01-01)과 상관없이 지난달이 덮이도록 시작일을 앞당기고, 끝나면 되돌린다
        originalPolicies = jdbcTemplate.queryForList("SELECT id, valid_from FROM rebate_policy");
        jdbcTemplate.update("UPDATE rebate_policy SET valid_from = DATE '2020-01-01' WHERE valid_from > DATE '2020-01-01'");

        adminId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE login_id = 'admin'", Long.class);
        String orderNo = "BT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update("INSERT INTO sales_order (order_no, partner_id, partner_grade_code, status, total_amount, approved_at) "
                + "SELECT ?, id, grade_code, 'APPROVED', 1290000, now() FROM partner WHERE code = 'P0001'", orderNo);
        orderId = jdbcTemplate.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
        jdbcTemplate.update("INSERT INTO sales_order_item (order_id, line_no, product_id, qty, unit_price, amount) "
                + "SELECT ?, 1, id, 1, 1290000, 1290000 FROM product WHERE sku = 'NB-14-001'", orderId);
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        // 마감을 먼저 지워야 그 달 주문을 지울 수 있다 (마감월 보호 트리거)
        jdbcTemplate.update("DELETE FROM settlement_detail WHERE yyyymm = ?", prev);
        jdbcTemplate.update("DELETE FROM settlement WHERE yyyymm = ?", prev);
        jdbcTemplate.update("DELETE FROM settlement_run_log WHERE yyyymm = ?", prev);
        jdbcTemplate.update("DELETE FROM settlement_period WHERE yyyymm = ?", prev);
        jdbcTemplate.update("DELETE FROM sales_order_item WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM sales_order WHERE id = ?", orderId);
        for (Map<String, Object> p : originalPolicies) {
            jdbcTemplate.update("UPDATE rebate_policy SET valid_from = ? WHERE id = ?", p.get("valid_from"), p.get("id"));
        }
    }

    @Test
    @DisplayName("출고 트랜잭션이 커밋 전일 때 마감하면, 마감은 출고 커밋을 기다렸다가 그 출고까지 포함해 정산한다")
    void closeWaitsForInFlightShipment() throws Exception {
        try (Connection ship = dataSource.getConnection()) {
            ship.setAutoCommit(false);
            shipAt(ship, lastMoment);

            Future<?> close = pool.submit(() -> jdbcTemplate.update("CALL run_settlement(?, ?, true)", prev, adminId));
            assertThatThrownBy(() -> close.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            ship.commit();
            close.get(10, TimeUnit.SECONDS);
        }

        assertThat(periodStatus()).isEqualTo("CLOSED");
        assertThat(shippedLinesInSettlement()).isEqualTo(1);
    }

    @Test
    @DisplayName("마감 트랜잭션이 커밋 전일 때 출고하면, 출고는 마감 커밋을 기다렸다가 마감된 달이라 거부된다")
    void shipmentWaitsForCloseAndIsRejected() throws Exception {
        try (Connection close = dataSource.getConnection()) {
            close.setAutoCommit(false);
            try (PreparedStatement ps = close.prepareStatement("CALL run_settlement(?, ?, true)")) {
                ps.setString(1, prev);
                ps.setLong(2, adminId);
                ps.execute();
            }

            Future<?> ship = pool.submit(() -> jdbcTemplate.update(
                    "UPDATE sales_order SET status = 'SHIPPED', shipped_at = ? WHERE id = ?", lastMoment, orderId));
            assertThatThrownBy(() -> ship.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            close.commit();
            assertThatThrownBy(() -> ship.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("마감된 정산월");
        }

        String status = jdbcTemplate.queryForObject("SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
        assertThat(status).isEqualTo("APPROVED");
    }

    private void shipAt(Connection connection, LocalDateTime at) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sales_order SET status = 'SHIPPED', shipped_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(at));
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
    }

    private String periodStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM settlement_period WHERE yyyymm = ?", String.class, prev);
    }

    private int shippedLinesInSettlement() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM settlement_detail d JOIN sales_order_item i ON i.id = d.source_id "
                        + "WHERE d.yyyymm = ? AND d.source_type = 'SHIP' AND i.order_id = ?",
                Integer.class, prev, orderId);
    }
}

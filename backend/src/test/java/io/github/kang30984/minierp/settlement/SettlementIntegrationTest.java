package io.github.kang30984.minierp.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.kang30984.minierp.IntegrationTestSupport;
import io.github.kang30984.minierp.common.TimeConfig;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class SettlementIntegrationTest extends IntegrationTestSupport {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final YearMonth current = YearMonth.now(TimeConfig.BUSINESS_ZONE);
    private final YearMonth previous = current.minusMonths(1);
    private final String prev = previous.format(YYYYMM);
    private final String now = current.format(YYYYMM);

    private MockHttpSession admin;
    private MockHttpSession partner1;
    private MockHttpSession partner2;

    @BeforeEach
    void setUp() throws Exception {
        // 데모 정책은 2026-01-01부터라서, 테스트를 언제 돌려도 지난달이 덮이도록 시작일을 앞당긴다
        jdbcTemplate.update("UPDATE rebate_policy SET valid_from = DATE '2020-01-01'");
        admin = loginAsAdmin();
        partner1 = loginAs("partner1");
        partner2 = loginAs("partner2");
    }

    /** 출고까지 마친 주문의 출고일을 지난달 특정 날짜로 옮긴다 */
    private JsonNode shippedLastMonth(MockHttpSession partner, String sku, int qty, int day) throws Exception {
        JsonNode order = shippedOrder(partner, admin, sku, qty);
        jdbcTemplate.update("UPDATE sales_order SET shipped_at = ? WHERE id = ?",
                previous.atDay(day).atTime(10, 0), order.get("id").asLong());
        return order;
    }

    @Test
    @DisplayName("미리보기: 거래처별로 출고 금액과 등급별 리베이트를 집계한다")
    void preview() throws Exception {
        shippedLastMonth(partner1, "NB-14-001", 2, 10);   // GOLD 2%: 2,580,000 → 51,600
        shippedLastMonth(partner2, "MN-27-QHD", 1, 12);   // SILVER 1%: 389,000 → 3,890

        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closable").value(true))
                .andExpect(jsonPath("$.summaries.length()").value(2))
                .andExpect(jsonPath("$.summaries[0].partnerName").value("한빛시스템"))
                .andExpect(jsonPath("$.summaries[0].rebateAmount").value(51_600))
                .andExpect(jsonPath("$.summaries[1].rebateAmount").value(3_890))
                .andExpect(jsonPath("$.totals.netAmount").value(2_969_000))
                .andExpect(jsonPath("$.totals.rebateAmount").value(55_490))
                .andExpect(jsonPath("$.runs[0].runType").value("PREVIEW"));
    }

    @Test
    @DisplayName("미리보기를 여러 번 실행해도 결과가 쌓이지 않는다")
    void previewIsIdempotent() throws Exception {
        shippedLastMonth(partner1, "NB-14-001", 1, 5);

        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin))
                .andExpect(jsonPath("$.totals.netAmount").value(1_290_000))
                .andExpect(jsonPath("$.runs.length()").value(2));

        Integer details = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM settlement_detail WHERE yyyymm = ?", Integer.class, prev);
        assertThat(details).isEqualTo(1);
    }

    @Test
    @DisplayName("월 중간에 요율이 바뀌면 출고일에 적용 중이던 요율로 계산한다")
    void midMonthRateChange() throws Exception {
        String changeDay = previous.atDay(16).toString();
        jdbcTemplate.update("UPDATE rebate_policy SET valid_to = ?::date WHERE grade_code = 'GOLD'", changeDay);
        jdbcTemplate.update("INSERT INTO rebate_policy (grade_code, rebate_rate, valid_from) VALUES ('GOLD', 0.03, ?::date)",
                changeDay);

        shippedLastMonth(partner1, "NB-14-001", 1, 10);   // 2%: 1,290,000 → 25,800
        shippedLastMonth(partner1, "MN-27-QHD", 1, 20);   // 3%:   389,000 → 11,670

        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin))
                .andExpect(jsonPath("$.summaries[0].rebateAmount").value(37_470));

        long partnerId = jdbcTemplate.queryForObject("SELECT id FROM partner WHERE code = 'P0001'", Long.class);
        mvc.perform(get("/api/admin/settlements/{m}/partners/{p}", prev, partnerId).session(admin))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rebateRate").value(0.02))
                .andExpect(jsonPath("$[1].rebateRate").value(0.03));
    }

    @Test
    @DisplayName("지난달 출고분을 이번 달에 반품하면 이번 달 정산에서 원래 요율로 차감한다")
    void returnDeductedInReturnMonth() throws Exception {
        JsonNode order = shippedLastMonth(partner1, "NB-14-001", 2, 10);
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin));
        mvc.perform(post("/api/admin/settlements/{m}/close", prev).with(csrf()).session(admin))
                .andExpect(status().isOk());

        // 지난달이 마감됐어도 반품일(오늘)이 속한 이번 달로 들어가므로 등록할 수 있다
        mvc.perform(post("/api/admin/orders/{id}/returns", order.get("id").asLong()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lineNo", 1, "qty", 1, "reason", "초기 불량"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/settlements/{m}/preview", now).with(csrf()).session(admin))
                .andExpect(jsonPath("$.summaries[0].shipAmount").value(0))
                .andExpect(jsonPath("$.summaries[0].returnAmount").value(1_290_000))
                .andExpect(jsonPath("$.summaries[0].netAmount").value(-1_290_000))
                .andExpect(jsonPath("$.summaries[0].rebateAmount").value(-25_800));
    }

    @Test
    @DisplayName("마감하면 다시 실행할 수 없다")
    void closeThenRerun() throws Exception {
        shippedLastMonth(partner1, "NB-14-001", 1, 3);
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin));

        mvc.perform(post("/api/admin/settlements/{m}/close", prev).with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedByName").value("운영관리자"))
                .andExpect(jsonPath("$.closable").value(false))
                .andExpect(jsonPath("$.previewable").value(false));

        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("마감된 달의 반품 데이터는 SQL로 직접 넣어도 DB 트리거가 막는다")
    void triggerBlocksClosedMonth() throws Exception {
        JsonNode order = shippedLastMonth(partner1, "NB-14-001", 1, 3);
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin));
        mvc.perform(post("/api/admin/settlements/{m}/close", prev).with(csrf()).session(admin));

        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM sales_order_item WHERE order_id = ?", Long.class, order.get("id").asLong());
        LocalDateTime inClosedMonth = previous.atDay(20).atTime(9, 0);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO sales_return (order_item_id, qty, amount, returned_at, created_by) VALUES (?, 1, 1290000, ?, 1)",
                itemId, inClosedMonth))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("마감된 정산월");
    }

    /** 지난달 출고 주문 하나를 만들고 지난달 정산을 마감한다 */
    private JsonNode closedLastMonthOrder() throws Exception {
        JsonNode order = shippedLastMonth(partner1, "NB-14-001", 1, 3);
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin));
        mvc.perform(post("/api/admin/settlements/{m}/close", prev).with(csrf()).session(admin))
                .andExpect(status().isOk());
        return order;
    }

    @Test
    @DisplayName("마감된 달에 출고된 주문 줄의 금액은 SQL로 직접 바꿔도 DB 트리거가 막는다")
    void triggerBlocksItemChangeInClosedMonth() throws Exception {
        JsonNode order = closedLastMonthOrder();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE sales_order_item SET amount = 1 WHERE order_id = ?", order.get("id").asLong()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("마감된 정산월");
    }

    @Test
    @DisplayName("마감된 달 출고일을 가진 주문은 SQL로 직접 넣어도 DB 트리거가 막는다")
    void triggerBlocksOrderInsertInClosedMonth() throws Exception {
        closedLastMonthOrder();
        LocalDateTime inClosedMonth = previous.atDay(20).atTime(9, 0);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO sales_order (order_no, partner_id, partner_grade_code, status, total_amount, shipped_at) "
                        + "SELECT 'SO-TEST-CLOSED', id, grade_code, 'SHIPPED', 1290000, ? FROM partner WHERE code = 'P0001'",
                inClosedMonth))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("마감된 정산월");
    }

    @Test
    @DisplayName("마감된 달에 적용되던 리베이트 요율은 SQL로 직접 바꿔도 DB 트리거가 막는다")
    void triggerBlocksRateChangeCoveringClosedMonth() throws Exception {
        closedLastMonthOrder();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE rebate_policy SET rebate_rate = 0.05 WHERE grade_code = 'GOLD'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("리베이트 정책");
    }

    @Test
    @DisplayName("마감 후에도 내일부터 적용할 새 요율은 등록할 수 있다")
    void futureRateCanBeRegisteredAfterClose() throws Exception {
        closedLastMonthOrder();

        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gradeCode", "GOLD", "rebateRate", 0.025,
                                "validFrom", current.atDay(1).plusMonths(1).toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("진행 중인 달은 마감할 수 없고, 미래 달은 미리보기도 할 수 없다")
    void monthRules() throws Exception {
        mvc.perform(post("/api/admin/settlements/{m}/preview", now).with(csrf()).session(admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/settlements/{m}/close", now).with(csrf()).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTH_IN_PROGRESS"));
        mvc.perform(post("/api/admin/settlements/{m}/preview", current.plusMonths(1).format(YYYYMM))
                        .with(csrf()).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_MONTH"));
        mvc.perform(post("/api/admin/settlements/{m}/close", prev).with(csrf()).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREVIEW_REQUIRED"));
        mvc.perform(get("/api/admin/settlements/{m}", "2026-9").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
    }

    @Test
    @DisplayName("정산 결과를 엑셀에서 열 수 있는 CSV로 내려받는다")
    void csv() throws Exception {
        shippedLastMonth(partner1, "NB-14-001", 1, 3);
        mvc.perform(post("/api/admin/settlements/{m}/preview", prev).with(csrf()).session(admin));

        byte[] body = mvc.perform(get("/api/admin/settlements/{m}/csv", prev).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(body, StandardCharsets.UTF_8);

        assertThat(text).startsWith("\uFEFF정산월,거래처코드");
        assertThat(text).contains("\"한빛시스템\",1290000,0,1290000,25800");
        assertThat(text).contains(",합계,,1290000");
    }

    @Test
    @DisplayName("정산 실행 전인 달은 NONE 상태로 비어 있다")
    void emptyMonth() throws Exception {
        mvc.perform(get("/api/admin/settlements/{m}", prev).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.previewable").value(true))
                .andExpect(jsonPath("$.summaries.length()").value(0))
                .andExpect(jsonPath("$.totals.netAmount").value(0));
    }
}

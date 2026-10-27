package io.github.kang30984.minierp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.kang30984.minierp.IntegrationTestSupport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AdminOrderIntegrationTest extends IntegrationTestSupport {

    private MockHttpSession admin;
    private MockHttpSession partner1;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAsAdmin();
        partner1 = loginAs("partner1");
    }

    private Map<String, Object> action(JsonNode order, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", order.get("version").asInt());
        body.put("reason", reason);
        return body;
    }

    @Test
    @DisplayName("승인하면 재고가 차감되고 승인일시와 이력이 남는다")
    void approve() throws Exception {
        int before = stockOf("NB-14-001");
        JsonNode order = placeOrder(partner1, "NB-14-001", 3);

        JsonNode approved = adminAction(admin, order, "approve");

        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedAt").isNull()).isFalse();
        assertThat(approved.get("version").asInt()).isEqualTo(order.get("version").asInt() + 1);
        assertThat(approved.get("histories")).hasSize(2);
        assertThat(stockOf("NB-14-001")).isEqualTo(before - 3);
    }

    @Test
    @DisplayName("화면을 연 뒤 다른 사용자가 먼저 처리했으면 409 STALE_ORDER")
    void staleVersion() throws Exception {
        JsonNode order = placeOrder(partner1, "NB-14-001", 1);
        adminAction(admin, order, "approve");

        // 처음 받은(이전) 버전으로 다시 처리 시도
        mvc.perform(post("/api/admin/orders/{id}/reject", order.get("id").asLong()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(order, "중복 처리"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ORDER"));
    }

    @Test
    @DisplayName("이미 승인된 주문은 다시 승인할 수 없다")
    void approveTwice() throws Exception {
        JsonNode approved = adminAction(admin, placeOrder(partner1, "NB-14-001", 1), "approve");

        mvc.perform(post("/api/admin/orders/{id}/approve", approved.get("id").asLong()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(approved, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("주문할 때는 재고가 있었어도 승인 시점에 부족하면 409")
    void approveOutOfStock() throws Exception {
        // 서버 재고 10대. 각각 6대 주문은 접수되지만 둘 다 승인할 수는 없다
        JsonNode first = placeOrder(partner1, "SV-1U-01", 6);
        JsonNode second = placeOrder(loginAs("partner2"), "SV-1U-01", 6);
        adminAction(admin, first, "approve");

        mvc.perform(post("/api/admin/orders/{id}/approve", second.get("id").asLong()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(second, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("반려할 때는 사유가 필요하고, 사유는 거래처 화면 이력에도 보인다")
    void reject() throws Exception {
        JsonNode order = placeOrder(partner1, "MN-27-QHD", 2);
        long id = order.get("id").asLong();

        mvc.perform(post("/api/admin/orders/{id}/reject", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(order, " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        mvc.perform(post("/api/admin/orders/{id}/reject", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(order, "단가 협의 필요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get("/api/market/orders/{id}", id).session(partner1))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.histories[1].reason").value("단가 협의 필요"));
    }

    @Test
    @DisplayName("출고하면 출고일시가 기록된다")
    void ship() throws Exception {
        JsonNode shipped = shippedOrder(partner1, admin, "DT-MINI-01", 1);

        assertThat(shipped.get("status").asText()).isEqualTo("SHIPPED");
        assertThat(shipped.get("shippedAt").isNull()).isFalse();
        assertThat(shipped.get("histories")).hasSize(3);
    }

    @Test
    @DisplayName("승인된 주문을 취소하면 차감했던 재고가 돌아온다")
    void cancelApproved() throws Exception {
        int before = stockOf("NW-SW-24");
        JsonNode approved = adminAction(admin, placeOrder(partner1, "NW-SW-24", 5), "approve");
        assertThat(stockOf("NW-SW-24")).isEqualTo(before - 5);

        JsonNode canceled = adminAction(admin, approved, "cancel");

        assertThat(canceled.get("status").asText()).isEqualTo("CANCELED");
        assertThat(stockOf("NW-SW-24")).isEqualTo(before);
    }

    @Test
    @DisplayName("부분 반품은 여러 번 할 수 있지만 주문 수량을 넘을 수는 없다")
    void partialReturns() throws Exception {
        JsonNode shipped = shippedOrder(partner1, admin, "MN-27-QHD", 3);
        long id = shipped.get("id").asLong();
        int afterShip = stockOf("MN-27-QHD");

        mvc.perform(post("/api/admin/orders/{id}/returns", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lineNo", 1, "qty", 2, "reason", "화면 불량"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].returnedQty").value(2))
                .andExpect(jsonPath("$.returns[0].amount").value(778_000));
        assertThat(stockOf("MN-27-QHD")).isEqualTo(afterShip + 2);

        mvc.perform(post("/api/admin/orders/{id}/returns", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lineNo", 1, "qty", 2, "reason", "추가 반품"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVER_RETURN"));
    }

    @Test
    @DisplayName("출고 전 주문은 반품할 수 없다")
    void returnBeforeShip() throws Exception {
        JsonNode order = placeOrder(partner1, "MN-27-QHD", 1);

        mvc.perform(post("/api/admin/orders/{id}/returns", order.get("id").asLong()).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lineNo", 1, "qty", 1, "reason", "테스트"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_SHIPPED"));
    }

    @Test
    @DisplayName("관리자 주문 목록은 모든 거래처 주문을 보여 주고 상태별 건수를 알려 준다")
    void searchAndCounts() throws Exception {
        placeOrder(partner1, "NB-14-001", 1);
        placeOrder(loginAs("partner2"), "NB-14-001", 1);

        mvc.perform(get("/api/admin/orders").param("status", "REQUESTED").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
        mvc.perform(get("/api/admin/orders").param("keyword", "새솔").session(admin))
                .andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/admin/orders/status-counts").session(admin))
                .andExpect(jsonPath("$.REQUESTED").value(2))
                .andExpect(jsonPath("$.SHIPPED").value(0));
    }

    @Test
    @DisplayName("거래처 사용자는 주문을 승인할 수 없다")
    void partnerCannotApprove() throws Exception {
        JsonNode order = placeOrder(partner1, "NB-14-001", 1);

        mvc.perform(post("/api/admin/orders/{id}/approve", order.get("id").asLong()).with(csrf()).session(partner1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action(order, null))))
                .andExpect(status().isForbidden());
    }
}

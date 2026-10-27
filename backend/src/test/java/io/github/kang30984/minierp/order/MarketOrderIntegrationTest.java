package io.github.kang30984.minierp.order;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.kang30984.minierp.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class MarketOrderIntegrationTest extends IntegrationTestSupport {

    private Map<String, Object> line(String sku, int qty) {
        return Map.of("productId", productId(sku), "qty", qty);
    }

    @SafeVarargs
    private Map<String, Object> order(Map<String, Object>... lines) {
        return Map.of("items", List.of(lines));
    }

    private long createOrder(MockHttpSession session, Map<String, Object> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/market/orders").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    @DisplayName("주문하면 DB 단가로 금액을 계산하고, 주문 시점 등급과 요청 이력을 남긴다")
    void createOrder() throws Exception {
        MockHttpSession partner1 = loginAs("partner1");

        // 1,290,000 x 2 + 389,000 x 3 = 3,747,000
        mvc.perform(post("/api/market/orders").with(csrf()).session(partner1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order(line("NB-14-001", 2), line("MN-27-QHD", 3)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo", matchesPattern("^SO\\d{8}-\\d{6}$")))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.partnerName").value("한빛시스템"))
                .andExpect(jsonPath("$.partnerGradeCode").value("GOLD"))
                .andExpect(jsonPath("$.totalAmount").value(3_747_000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].amount").value(1_167_000))
                .andExpect(jsonPath("$.histories.length()").value(1))
                .andExpect(jsonPath("$.histories[0].toStatus").value("REQUESTED"));
    }

    @Test
    @DisplayName("요청 본문에 단가를 넣어 보내도 무시하고 DB 단가를 쓴다")
    void clientPriceIgnored() throws Exception {
        Map<String, Object> tampered = Map.of("items", List.of(
                Map.of("productId", productId("SV-1U-01"), "qty", 1, "unitPrice", 1)));

        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tampered)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(6_890_000));
    }

    @Test
    @DisplayName("연속으로 주문해도 주문번호가 겹치지 않는다")
    void orderNoIsUnique() throws Exception {
        MockHttpSession partner1 = loginAs("partner1");
        createOrder(partner1, order(line("NW-AP-6E", 1)));
        createOrder(partner1, order(line("NW-AP-6E", 1)));

        mvc.perform(get("/api/market/orders").session(partner1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("재고보다 많이 주문하면 400")
    void outOfStock() throws Exception {
        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order(line("SV-1U-01", 11)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("판매 중지 상품은 주문할 수 없다")
    void stoppedProduct() throws Exception {
        jdbcTemplate.update("UPDATE product SET status = 'STOPPED' WHERE sku = 'MN-24-FHD'");

        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order(line("MN-24-FHD", 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("같은 상품을 두 줄로 나눠 주문하면 400")
    void duplicateLines() throws Exception {
        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order(line("NW-SW-24", 1), line("NW-SW-24", 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT"));
    }

    @Test
    @DisplayName("빈 주문은 400")
    void emptyOrder() throws Exception {
        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("다른 거래처의 주문은 조회도 취소도 404")
    void otherPartnerOrderIsHidden() throws Exception {
        long orderId = createOrder(loginAs("partner1"), order(line("NB-14-001", 1)));
        MockHttpSession partner2 = loginAs("partner2");

        mvc.perform(get("/api/market/orders/{id}", orderId).session(partner2))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/market/orders/{id}/cancel", orderId).with(csrf()).session(partner2))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/market/orders").session(partner2))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("요청 상태 주문은 취소할 수 있고, 이미 취소된 주문을 다시 취소하면 409")
    void cancel() throws Exception {
        MockHttpSession partner1 = loginAs("partner1");
        long orderId = createOrder(partner1, order(line("DT-MINI-01", 2)));

        mvc.perform(post("/api/market/orders/{id}/cancel", orderId).with(csrf()).session(partner1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.histories.length()").value(2))
                .andExpect(jsonPath("$.histories[1].fromStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.histories[1].toStatus").value("CANCELED"));

        mvc.perform(post("/api/market/orders/{id}/cancel", orderId).with(csrf()).session(partner1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CANCELABLE"));
    }

    @Test
    @DisplayName("상태로 주문 목록을 거를 수 있다")
    void filterByStatus() throws Exception {
        MockHttpSession partner1 = loginAs("partner1");
        long canceled = createOrder(partner1, order(line("ST-NAS-4B", 1)));
        createOrder(partner1, order(line("ST-NAS-4B", 1)));
        mvc.perform(post("/api/market/orders/{id}/cancel", canceled).with(csrf()).session(partner1))
                .andExpect(status().isOk());

        mvc.perform(get("/api/market/orders").param("status", "CANCELED").session(partner1))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(canceled));
        mvc.perform(get("/api/market/orders").param("status", "WRONG").session(partner1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자는 거래처 주문 API를 쓸 수 없다")
    void adminCannotOrder() throws Exception {
        mvc.perform(post("/api/market/orders").with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(order(line("NB-14-001", 1)))))
                .andExpect(status().isForbidden());
    }
}

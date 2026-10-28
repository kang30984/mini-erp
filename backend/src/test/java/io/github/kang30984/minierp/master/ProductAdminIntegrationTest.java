package io.github.kang30984.minierp.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kang30984.minierp.IntegrationTestSupport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class ProductAdminIntegrationTest extends IntegrationTestSupport {

    private Map<String, Object> product(String sku) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", "테스트 키보드");
        body.put("category", "NOTEBOOK");
        body.put("unitPrice", 59000);
        body.put("stockQty", 30);
        body.put("status", "ON_SALE");
        return body;
    }

    @Test
    @DisplayName("상품을 등록하면 목록 검색에 나온다")
    void createAndSearch() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mvc.perform(post("/api/admin/products").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("KB-TEST-01"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("KB-TEST-01"))
                .andExpect(jsonPath("$.unitPrice").value(59000));

        mvc.perform(get("/api/admin/products").param("keyword", "KB-TEST").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].name").value("테스트 키보드"));
    }

    @Test
    @DisplayName("검색어의 %는 와일드카드가 아니라 글자로 검색된다")
    void percentIsLiteral() throws Exception {
        mvc.perform(get("/api/admin/products").param("keyword", "%").session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("이미 있는 상품 코드로 등록하면 409")
    void duplicateSku() throws Exception {
        mvc.perform(post("/api/admin/products").with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("NB-14-001"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    @DisplayName("분류 값이 목록에 없으면 400")
    void invalidCategory() throws Exception {
        Map<String, Object> body = product("KB-TEST-02");
        body.put("category", "FOOD");

        mvc.perform(post("/api/admin/products").with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("상품을 수정해도 상품 코드는 바뀌지 않는다")
    void updateKeepsSku() throws Exception {
        long id = productId("MN-24-FHD");
        Map<String, Object> body = product("CHANGED-SKU");
        body.put("name", "24형 FHD 모니터 (단종)");
        body.put("status", "STOPPED");
        body.put("stockQty", stockOf(id));
        body.put("baseStockQty", stockOf(id));

        mvc.perform(put("/api/admin/products/{id}", id).with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("MN-24-FHD"))
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    @DisplayName("판매 중지 상품은 거래처 상품 목록에 나오지 않는다")
    void stoppedProductHiddenFromMarket() throws Exception {
        jdbcTemplate.update("UPDATE product SET status = 'STOPPED' WHERE sku = 'SV-1U-01'");

        mvc.perform(get("/api/market/products").param("size", "100").session(loginAs("partner1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].sku", hasItem("NB-14-001")))
                .andExpect(jsonPath("$.totalCount").value(9));
    }

    @Test
    @DisplayName("거래처 사용자는 상품을 등록할 수 없다")
    void partnerCannotCreate() throws Exception {
        mvc.perform(post("/api/admin/products").with(csrf()).session(loginAs("partner1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("KB-TEST-03"))))
                .andExpect(status().isForbidden());
    }

    // ---------- 재고 비교 후 교체 ----------

    @Test
    @DisplayName("화면을 연 뒤 승인 등으로 재고가 바뀌었으면, 예전 숫자로 재고를 덮어쓰지 않고 409로 거절한다")
    void staleStockIsRejected() throws Exception {
        long id = productId("MN-24-FHD");
        int seen = stockOf(id);
        // 관리자가 수정 화면을 연 뒤 다른 관리자의 승인으로 재고 3개가 차감됐다
        jdbcTemplate.update("UPDATE product SET stock_qty = stock_qty - 3 WHERE id = ?", id);

        Map<String, Object> body = product("MN-24-FHD");
        body.put("stockQty", seen + 10);      // 화면에서 본 숫자를 기준으로 입고 10개를 더했다고 생각하고 저장
        body.put("baseStockQty", seen);

        mvc.perform(put("/api/admin/products/{id}", id).with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_STOCK"));

        // 덮어쓰지 않았으므로 승인으로 줄어든 재고가 그대로다
        assertThat(stockOf(id)).isEqualTo(seen - 3);
    }

    @Test
    @DisplayName("재고를 바꾸지 않은 수정은 그 사이 재고가 바뀌어도 성공하고, 재고 칼럼은 건드리지 않는다")
    void editWithoutStockChangeKeepsCurrentStock() throws Exception {
        long id = productId("MN-24-FHD");
        int seen = stockOf(id);
        jdbcTemplate.update("UPDATE product SET stock_qty = stock_qty - 3 WHERE id = ?", id);

        Map<String, Object> body = product("MN-24-FHD");
        body.put("name", "24형 FHD 모니터 (가격 인하)");
        body.put("stockQty", seen);
        body.put("baseStockQty", seen);

        mvc.perform(put("/api/admin/products/{id}", id).with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("24형 FHD 모니터 (가격 인하)"))
                .andExpect(jsonPath("$.stockQty").value(seen - 3));
    }

    @Test
    @DisplayName("수정 요청에 기준 재고가 없으면 400")
    void baseStockRequiredOnUpdate() throws Exception {
        long id = productId("MN-24-FHD");
        Map<String, Object> body = product("MN-24-FHD");

        mvc.perform(put("/api/admin/products/{id}", id).with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BASE_STOCK_REQUIRED"));
    }

    private int stockOf(long id) {
        return jdbcTemplate.queryForObject("SELECT stock_qty FROM product WHERE id = ?", Integer.class, id);
    }
}

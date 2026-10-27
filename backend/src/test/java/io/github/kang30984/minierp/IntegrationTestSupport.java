package io.github.kang30984.minierp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 공통 설정.
 * - PostgreSQL 컨테이너를 테스트 전체에서 한 번만 띄우고, Spring 컨텍스트도 재사용한다.
 * - 테스트마다 트랜잭션을 걸고 끝나면 롤백해서 테스트끼리 데이터가 섞이지 않게 한다.
 *   (MockMvc 요청은 테스트와 같은 스레드에서 실행되므로 같은 트랜잭션 안에서 처리된다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestSupport {

    protected static final String ADMIN_PASSWORD = "admin1234!";
    protected static final String PARTNER_PASSWORD = "partner1234!";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected MockHttpSession loginAsAdmin() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected MockHttpSession loginAs(String partnerLoginId) throws Exception {
        return login(partnerLoginId, PARTNER_PASSWORD);
    }

    protected MockHttpSession login(String loginId, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected long productId(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    protected int stockOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT stock_qty FROM product WHERE sku = ?", Integer.class, sku);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 거래처로 한 품목 주문을 넣고 주문 상세를 돌려준다 */
    protected JsonNode placeOrder(MockHttpSession partner, String sku, int qty) throws Exception {
        Map<String, Object> body = Map.of("items", List.of(Map.of("productId", productId(sku), "qty", qty)));
        MvcResult result = mvc.perform(post("/api/market/orders").with(csrf()).session(partner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result);
    }

    /** 관리자 처리(approve, reject, ship, cancel)를 성공시키고 갱신된 주문 상세를 돌려준다 */
    protected JsonNode adminAction(MockHttpSession admin, JsonNode order, String action) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/orders/{id}/{action}", order.get("id").asLong(), action)
                        .with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", order.get("version").asInt(), "reason", "테스트"))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    /** 주문 → 승인 → 출고까지 진행 */
    protected JsonNode shippedOrder(MockHttpSession partner, MockHttpSession admin, String sku, int qty)
            throws Exception {
        JsonNode order = placeOrder(partner, sku, qty);
        order = adminAction(admin, order, "approve");
        return adminAction(admin, order, "ship");
    }
}

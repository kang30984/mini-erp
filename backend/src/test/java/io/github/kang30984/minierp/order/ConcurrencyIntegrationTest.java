package io.github.kang30984.minierp.order;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kang30984.minierp.IntegrationTestSupport;
import io.github.kang30984.minierp.auth.AppUser;
import io.github.kang30984.minierp.auth.UserMapper;
import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.order.admin.AdminOrderService;
import io.github.kang30984.minierp.order.admin.OrderActionRequest;
import io.github.kang30984.minierp.order.admin.ReturnRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시성 테스트.
 * 다른 통합 테스트는 테스트 트랜잭션 안에서 실행하고 롤백하지만, 여기서는 여러 스레드가 각자 트랜잭션을 열고
 * 실제로 커밋해야 잠금이 경쟁한다. 그래서 테스트 트랜잭션을 끄고, 끝나면 만든 데이터를 직접 지운다.
 * 서비스 메서드를 스레드마다 직접 호출해서 HTTP 계층 없이 DB 잠금 동작만 검증한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    OrderService orderService;

    @Autowired
    AdminOrderService adminOrderService;

    @Autowired
    UserMapper userMapper;

    private AppUser admin;
    private AppUser partner1;
    private AppUser partner2;
    private long productId;

    @BeforeEach
    void setUp() {
        admin = userMapper.findByLoginId("admin").orElseThrow();
        partner1 = userMapper.findByLoginId("partner1").orElseThrow();
        partner2 = userMapper.findByLoginId("partner2").orElseThrow();
        productId = createProduct(10);
    }

    @AfterEach
    void cleanUp() {
        List<Long> orderIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT order_id FROM sales_order_item WHERE product_id = ?", Long.class, productId);
        jdbcTemplate.update("DELETE FROM sales_return WHERE order_item_id IN "
                + "(SELECT id FROM sales_order_item WHERE product_id = ?)", productId);
        for (Long orderId : orderIds) {
            jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM sales_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM sales_order WHERE id = ?", orderId);
        }
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
    }

    @Test
    @DisplayName("재고 10개 상품의 1개짜리 주문 20건을 동시에 승인하면 정확히 10건만 승인된다")
    void concurrentApprovalsNeverOversell() throws Exception {
        List<OrderDetailResponse> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            orders.add(order(i % 2 == 0 ? partner1 : partner2, 1));
        }

        Outcome outcome = runConcurrently(orders.stream()
                .<Callable<Object>>map(o -> () -> adminOrderService.approve(admin, o.id(), action(o)))
                .toList());

        assertThat(outcome.successes()).isEqualTo(10);
        assertThat(outcome.errorCodes()).hasSize(10).containsOnly("OUT_OF_STOCK");
        assertThat(stock()).isZero();
        assertThat(countByStatus("APPROVED")).isEqualTo(10);
    }

    @Test
    @DisplayName("관리자 5명이 같은 주문을 동시에 승인해도 한 번만 승인되고 재고도 한 번만 차감된다")
    void sameOrderApprovedOnce() throws Exception {
        OrderDetailResponse order = order(partner1, 3);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> adminOrderService.approve(admin, order.id(), action(order)));
        }
        Outcome outcome = runConcurrently(tasks);

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.errorCodes()).hasSize(4).containsOnly("STALE_ORDER");
        assertThat(stock()).isEqualTo(7);
        Integer histories = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'APPROVED'",
                Integer.class, order.id());
        assertThat(histories).isEqualTo(1);
    }

    @Test
    @DisplayName("거래처 취소와 관리자 승인이 동시에 들어오면 둘 중 하나만 반영되고 재고가 맞는다")
    void cancelAndApproveRace() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            OrderDetailResponse order = order(partner1, 1);
            tasks.add(() -> orderService.cancelMyOrder(partner1, order.id()));
            tasks.add(() -> adminOrderService.approve(admin, order.id(), action(order)));
        }

        Outcome outcome = runConcurrently(tasks);

        int approved = countByStatus("APPROVED");
        int canceled = countByStatus("CANCELED");
        assertThat(outcome.successes()).isEqualTo(10);
        assertThat(outcome.errorCodes()).hasSize(10).containsOnly("STALE_ORDER", "NOT_CANCELABLE");
        assertThat(approved + canceled).isEqualTo(10);
        assertThat(stock()).isEqualTo(10 - approved);
    }

    @Test
    @DisplayName("주문 수량 5개인 줄에 1개씩 반품 10건을 동시에 넣어도 5건만 등록된다")
    void concurrentReturnsNeverExceedQty() throws Exception {
        OrderDetailResponse order = order(partner1, 5);
        order = adminOrderService.approve(admin, order.id(), action(order));
        OrderDetailResponse shipped = adminOrderService.ship(admin, order.id(), action(order));

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> adminOrderService.registerReturn(admin, shipped.id(), new ReturnRequest(1, 1, "동시 반품")));
        }
        Outcome outcome = runConcurrently(tasks);

        assertThat(outcome.successes()).isEqualTo(5);
        assertThat(outcome.errorCodes()).hasSize(5).containsOnly("OVER_RETURN");
        Integer returned = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(r.qty), 0) FROM sales_return r JOIN sales_order_item i ON i.id = r.order_item_id "
                        + "WHERE i.order_id = ?", Integer.class, shipped.id());
        assertThat(returned).isEqualTo(5);
        // 승인 때 5개 차감, 반품 5개 복원
        assertThat(stock()).isEqualTo(10);
    }

    // ---------- 도우미 ----------

    private record Outcome(int successes, List<String> errorCodes) {
    }

    /**
     * 모든 작업을 스레드에 올려 놓고 출발 신호를 한 번에 줘서 최대한 동시에 실행한다.
     * 업무 예외(BusinessException)는 에러 코드로 모으고, 그 밖의 예외는 테스트를 실패시킨다.
     */
    private Outcome runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            int successes = 0;
            List<String> errorCodes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof BusinessException be) {
                        errorCodes.add(be.getCode());
                    } else {
                        throw e;
                    }
                }
            }
            return new Outcome(successes, errorCodes);
        } finally {
            pool.shutdownNow();
        }
    }

    private long createProduct(int stock) {
        String sku = "CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update("INSERT INTO product (sku, name, category, unit_price, stock_qty) "
                + "VALUES (?, '동시성 테스트 상품', 'NETWORK', 10000, ?)", sku, stock);
        return productId(sku);
    }

    private OrderDetailResponse order(AppUser partner, int qty) {
        return orderService.create(partner,
                new CreateOrderRequest(List.of(new CreateOrderRequest.Line(productId, qty))));
    }

    private static OrderActionRequest action(OrderDetailResponse order) {
        return new OrderActionRequest(order.version(), null);
    }

    private int stock() {
        return jdbcTemplate.queryForObject("SELECT stock_qty FROM product WHERE id = ?", Integer.class, productId);
    }

    private int countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT o.id) FROM sales_order o JOIN sales_order_item i ON i.order_id = o.id "
                        + "WHERE i.product_id = ? AND o.status = ?", Integer.class, productId, status);
    }
}

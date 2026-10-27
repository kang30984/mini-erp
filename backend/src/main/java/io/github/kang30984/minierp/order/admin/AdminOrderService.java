package io.github.kang30984.minierp.order.admin;

import io.github.kang30984.minierp.auth.AppUser;
import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.common.SearchText;
import io.github.kang30984.minierp.master.product.ProductMapper;
import io.github.kang30984.minierp.order.OrderDetailLoader;
import io.github.kang30984.minierp.order.OrderDetailResponse;
import io.github.kang30984.minierp.order.OrderHeader;
import io.github.kang30984.minierp.order.OrderItemResponse;
import io.github.kang30984.minierp.order.OrderMapper;
import io.github.kang30984.minierp.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    private final AdminOrderMapper adminOrderMapper;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final OrderDetailLoader detailLoader;

    public AdminOrderService(AdminOrderMapper adminOrderMapper, OrderMapper orderMapper,
                             ProductMapper productMapper, OrderDetailLoader detailLoader) {
        this.adminOrderMapper = adminOrderMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.detailLoader = detailLoader;
    }

    public PageResponse<AdminOrderSummary> search(String keyword, String status, LocalDate from, LocalDate to,
                                                  PageParams page) {
        String pattern = SearchText.containsPattern(keyword);
        String statusCond = OrderStatus.parseFilter(status);
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("INVALID_PERIOD", "조회 시작일이 종료일보다 늦습니다.");
        }
        long total = adminOrderMapper.count(pattern, statusCond, from, to);
        List<AdminOrderSummary> items = total == 0
                ? List.of()
                : adminOrderMapper.search(pattern, statusCond, from, to, page.size(), page.offset());
        return PageResponse.of(items, page, total);
    }

    /** 상태별 건수. 화면 탭에 "승인 대기 3"처럼 보여 준다. 건수가 0인 상태도 0으로 채운다 */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        adminOrderMapper.countByStatus().forEach(c -> counts.put(c.status(), c.count()));
        return counts;
    }

    public OrderDetailResponse get(long orderId) {
        return detailLoader.load(orderId, null);
    }

    /**
     * 승인: REQUESTED → APPROVED, 재고 차감.
     * 주문 행을 먼저 바꾸고(잠금) 상품 재고를 상품 ID 순서로 차감한다.
     * 모든 처리에서 잠금 순서를 "주문 → 상품(ID 오름차순)"으로 맞춰서 교착 상태를 피한다.
     * 재고가 하나라도 부족하면 예외로 트랜잭션 전체를 되돌린다.
     */
    @Transactional
    public OrderDetailResponse approve(AppUser admin, long orderId, OrderActionRequest request) {
        transition(admin, orderId, request, OrderStatus.REQUESTED, OrderStatus.APPROVED, null);

        List<OrderItemResponse> items = orderMapper.findItems(orderId).stream()
                .sorted(Comparator.comparing(OrderItemResponse::productId))
                .toList();
        for (OrderItemResponse item : items) {
            int updated = productMapper.decreaseStock(item.productId(), item.qty());
            if (updated == 0) {
                throw BusinessException.conflict("OUT_OF_STOCK",
                        "%s 재고가 부족해서 승인할 수 없습니다. (주문 %d개)".formatted(item.productName(), item.qty()));
            }
        }
        return detailLoader.load(orderId, null);
    }

    @Transactional
    public OrderDetailResponse reject(AppUser admin, long orderId, OrderActionRequest request) {
        String reason = SearchText.blankToNull(request.reason());
        if (reason == null) {
            throw BusinessException.badRequest("REASON_REQUIRED", "반려 사유를 입력해 주세요. 거래처 화면에 함께 표시됩니다.");
        }
        transition(admin, orderId, request, OrderStatus.REQUESTED, OrderStatus.REJECTED, reason);
        return detailLoader.load(orderId, null);
    }

    /** 출고: APPROVED → SHIPPED. 출고일시가 정산 기준일이 된다 */
    @Transactional
    public OrderDetailResponse ship(AppUser admin, long orderId, OrderActionRequest request) {
        transition(admin, orderId, request, OrderStatus.APPROVED, OrderStatus.SHIPPED, null);
        return detailLoader.load(orderId, null);
    }

    /** 승인 취소: APPROVED → CANCELED, 차감했던 재고 복원 */
    @Transactional
    public OrderDetailResponse cancel(AppUser admin, long orderId, OrderActionRequest request) {
        transition(admin, orderId, request, OrderStatus.APPROVED, OrderStatus.CANCELED,
                SearchText.blankToNull(request.reason()));
        orderMapper.findItems(orderId).stream()
                .sorted(Comparator.comparing(OrderItemResponse::productId))
                .forEach(item -> productMapper.increaseStock(item.productId(), item.qty()));
        return detailLoader.load(orderId, null);
    }

    /**
     * 반품 등록. 출고 완료 주문만 가능하고, 줄별 반품 수량 합계는 주문 수량을 넘을 수 없다.
     * 같은 줄을 동시에 반품하는 경우를 막으려고 주문 줄을 FOR UPDATE로 잠근 뒤 합계를 확인한다.
     * 반품된 상품은 다시 판매할 수 있다고 보고 재고를 되돌린다.
     */
    @Transactional
    public OrderDetailResponse registerReturn(AppUser admin, long orderId, ReturnRequest request) {
        OrderHeader header = orderMapper.findHeader(orderId, null)
                .orElseThrow(() -> BusinessException.notFound("주문을 찾을 수 없습니다."));
        if (!OrderStatus.SHIPPED.name().equals(header.status())) {
            throw BusinessException.conflict("NOT_SHIPPED", "출고 완료된 주문만 반품할 수 있습니다.");
        }

        ReturnTarget target = adminOrderMapper.findReturnTargetForUpdate(orderId, request.lineNo())
                .orElseThrow(() -> BusinessException.badRequest("INVALID_LINE", "주문에 없는 상품 줄입니다."));
        // READ COMMITTED에서는 문장마다 새 스냅샷을 쓴다.
        // 잠금 조회와 같은 문장에서 합계를 구하면, 잠금을 기다리는 동안 다른 트랜잭션이 커밋한 반품이 빠질 수 있다.
        int returned = adminOrderMapper.sumReturnedQty(target.itemId());
        int returnable = target.qty() - returned;
        if (request.qty() > returnable) {
            throw BusinessException.badRequest("OVER_RETURN",
                    "반품할 수 있는 수량은 %d개입니다. (주문 %d개, 이미 반품 %d개)"
                            .formatted(returnable, target.qty(), returned));
        }

        BigDecimal amount = target.unitPrice().multiply(BigDecimal.valueOf(request.qty()));
        adminOrderMapper.insertReturn(target.itemId(), request.qty(), amount, request.reason().strip(), admin.id());
        productMapper.increaseStock(target.productId(), request.qty());
        return detailLoader.load(orderId, null);
    }

    private void transition(AppUser admin, long orderId, OrderActionRequest request,
                            OrderStatus from, OrderStatus to, String reason) {
        int updated = adminOrderMapper.transition(orderId, request.version(), from.name(), to.name());
        if (updated == 0) {
            OrderHeader current = orderMapper.findHeader(orderId, null)
                    .orElseThrow(() -> BusinessException.notFound("주문을 찾을 수 없습니다."));
            if (!current.version().equals(request.version())) {
                throw BusinessException.conflict("STALE_ORDER",
                        "다른 사용자가 먼저 이 주문을 처리했습니다. 화면을 새로 고친 뒤 다시 확인해 주세요.");
            }
            throw BusinessException.conflict("INVALID_STATUS",
                    "현재 상태(%s)에서는 처리할 수 없습니다.".formatted(current.status()));
        }
        orderMapper.insertHistory(orderId, from.name(), to.name(), reason, admin.id());
    }
}

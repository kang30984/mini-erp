package io.github.kang30984.minierp.order;

import io.github.kang30984.minierp.auth.AppUser;
import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.master.partner.PartnerMapper;
import io.github.kang30984.minierp.master.partner.PartnerResponse;
import io.github.kang30984.minierp.master.product.ProductMapper;
import io.github.kang30984.minierp.master.product.ProductResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final PartnerMapper partnerMapper;
    private final OrderDetailLoader detailLoader;

    public OrderService(OrderMapper orderMapper, ProductMapper productMapper, PartnerMapper partnerMapper,
                        OrderDetailLoader detailLoader) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.partnerMapper = partnerMapper;
        this.detailLoader = detailLoader;
    }

    /**
     * 거래처 주문 등록.
     * - 거래처 ID는 요청 본문이 아니라 로그인 세션에서 가져온다. (다른 거래처 이름으로 주문하는 것 방지)
     * - 단가는 DB의 현재 단가를 쓴다. (클라이언트가 가격을 조작하는 것 방지)
     * - 주문 시점의 거래처 등급을 주문에 저장한다. (나중에 등급이 바뀌어도 이 주문의 정산 기준은 유지)
     * - 재고는 여기서 차감하지 않는다. 관리자 승인 시점에 차감하고, 여기서는 현재 재고를 넘는지만 확인한다.
     */
    @Transactional
    public OrderDetailResponse create(AppUser user, CreateOrderRequest request) {
        PartnerResponse partner = activePartner(user);

        Set<Long> productIds = new LinkedHashSet<>();
        for (CreateOrderRequest.Line line : request.items()) {
            if (!productIds.add(line.productId())) {
                throw BusinessException.badRequest("DUPLICATE_PRODUCT",
                        "같은 상품이 여러 줄에 있습니다. 수량을 합쳐서 한 줄로 주문해 주세요.");
            }
        }

        Map<Long, ProductResponse> products = productMapper.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        List<NewOrderItem> items = new ArrayList<>();
        int lineNo = 1;
        for (CreateOrderRequest.Line line : request.items()) {
            ProductResponse product = products.get(line.productId());
            if (product == null || !"ON_SALE".equals(product.status())) {
                throw BusinessException.badRequest("PRODUCT_UNAVAILABLE",
                        "판매 중이 아닌 상품이 포함되어 있습니다." + (product == null ? "" : " (" + product.name() + ")"));
            }
            if (line.qty() > product.stockQty()) {
                throw BusinessException.badRequest("OUT_OF_STOCK",
                        "%s 재고가 부족합니다. 주문 가능 수량은 %d개입니다.".formatted(product.name(), product.stockQty()));
            }
            items.add(new NewOrderItem(lineNo++, product.id(), line.qty(), product.unitPrice()));
        }

        BigDecimal total = items.stream()
                .map(NewOrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NewOrder order = new NewOrder(orderMapper.nextOrderNo(), partner.id(), partner.gradeCode(), total);
        orderMapper.insertOrder(order);
        orderMapper.insertItems(order.getId(), items);
        orderMapper.insertHistory(order.getId(), null, OrderStatus.REQUESTED.name(), null, user.id());

        return detailLoader.load(order.getId(), partner.id());
    }

    public PageResponse<OrderSummaryResponse> myOrders(AppUser user, String status, PageParams page) {
        long partnerId = requirePartnerId(user);
        String statusCond = OrderStatus.parseFilter(status);

        long total = orderMapper.countByPartner(partnerId, statusCond);
        List<OrderSummaryResponse> items = total == 0
                ? List.of()
                : orderMapper.findByPartner(partnerId, statusCond, page.size(), page.offset());
        return PageResponse.of(items, page, total);
    }

    public OrderDetailResponse myOrder(AppUser user, long orderId) {
        return detailLoader.load(orderId, requirePartnerId(user));
    }

    /**
     * 거래처 주문 취소. REQUESTED 상태에서만 가능하다.
     * "상태 확인 → 변경"을 따로 하지 않고 조건부 UPDATE 한 번으로 처리해서,
     * 관리자가 같은 순간에 승인하더라도 둘 중 하나만 반영되게 한다.
     */
    @Transactional
    public OrderDetailResponse cancelMyOrder(AppUser user, long orderId) {
        long partnerId = requirePartnerId(user);

        int updated = orderMapper.cancelRequested(orderId, partnerId);
        if (updated == 0) {
            OrderHeader header = orderMapper.findHeader(orderId, partnerId)
                    .orElseThrow(OrderDetailLoader::orderNotFound);
            throw BusinessException.conflict("NOT_CANCELABLE",
                    "요청 상태인 주문만 취소할 수 있습니다. 현재 상태: " + header.status());
        }
        orderMapper.insertHistory(orderId, OrderStatus.REQUESTED.name(), OrderStatus.CANCELED.name(),
                "거래처 취소", user.id());
        return detailLoader.load(orderId, partnerId);
    }

    private PartnerResponse activePartner(AppUser user) {
        PartnerResponse partner = partnerMapper.findById(requirePartnerId(user))
                .orElseThrow(() -> BusinessException.forbidden("PARTNER_NOT_FOUND", "거래처 정보를 찾을 수 없습니다."));
        // 로그인한 뒤에 거래처가 거래 중지된 경우를 막는다
        if (!"ACTIVE".equals(partner.status())) {
            throw BusinessException.forbidden("PARTNER_INACTIVE", "거래가 중지된 거래처는 주문할 수 없습니다.");
        }
        return partner;
    }

    private static long requirePartnerId(AppUser user) {
        if (user.partnerId() == null) {
            throw BusinessException.forbidden("PARTNER_ONLY", "거래처 사용자만 이용할 수 있습니다.");
        }
        return user.partnerId();
    }
}

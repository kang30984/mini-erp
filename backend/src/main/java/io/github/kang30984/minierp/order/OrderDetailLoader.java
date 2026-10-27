package io.github.kang30984.minierp.order;

import io.github.kang30984.minierp.common.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 주문 상세(헤더 + 상품 + 반품 + 처리 이력) 조회. 거래처 화면과 관리자 화면이 같이 쓴다.
 */
@Component
public class OrderDetailLoader {

    private final OrderMapper orderMapper;

    public OrderDetailLoader(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * partnerId가 있으면 그 거래처 주문만 찾는다.
     * 다른 거래처 주문은 403이 아니라 404로 응답한다. 주문이 존재한다는 사실 자체를 알려 주지 않기 위해서다.
     */
    public OrderDetailResponse load(long orderId, Long partnerId) {
        OrderHeader header = orderMapper.findHeader(orderId, partnerId)
                .orElseThrow(OrderDetailLoader::orderNotFound);
        return OrderDetailResponse.of(header,
                orderMapper.findItems(orderId),
                orderMapper.findReturns(orderId),
                orderMapper.findHistories(orderId));
    }

    static BusinessException orderNotFound() {
        return BusinessException.notFound("주문을 찾을 수 없습니다.");
    }
}

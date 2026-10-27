package io.github.kang30984.minierp.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String orderNo,
        Long partnerId,
        String partnerName,
        String partnerGradeCode,
        String status,
        BigDecimal totalAmount,
        LocalDateTime orderedAt,
        LocalDateTime approvedAt,
        LocalDateTime shippedAt,
        List<OrderItemResponse> items,
        List<OrderHistoryResponse> histories
) {

    static OrderDetailResponse of(OrderHeader h, List<OrderItemResponse> items,
                                  List<OrderHistoryResponse> histories) {
        return new OrderDetailResponse(h.id(), h.orderNo(), h.partnerId(), h.partnerName(),
                h.partnerGradeCode(), h.status(), h.totalAmount(), h.orderedAt(), h.approvedAt(),
                h.shippedAt(), items, histories);
    }
}

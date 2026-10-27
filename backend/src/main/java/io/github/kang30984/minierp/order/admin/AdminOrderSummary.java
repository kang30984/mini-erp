package io.github.kang30984.minierp.order.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderSummary(
        Long id,
        String orderNo,
        String partnerName,
        String status,
        BigDecimal totalAmount,
        Integer itemCount,
        String firstProductName,
        LocalDateTime orderedAt
) {
}

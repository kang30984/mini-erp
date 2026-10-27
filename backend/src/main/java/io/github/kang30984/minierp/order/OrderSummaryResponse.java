package io.github.kang30984.minierp.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String orderNo,
        String status,
        BigDecimal totalAmount,
        Integer itemCount,
        String firstProductName,
        LocalDateTime orderedAt
) {
}

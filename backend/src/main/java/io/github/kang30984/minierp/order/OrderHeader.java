package io.github.kang30984.minierp.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderHeader(
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
        Integer version
) {
}

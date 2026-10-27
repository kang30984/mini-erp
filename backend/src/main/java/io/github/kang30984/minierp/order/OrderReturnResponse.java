package io.github.kang30984.minierp.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderReturnResponse(
        Long id,
        Integer lineNo,
        String productName,
        Integer qty,
        BigDecimal amount,
        String reason,
        String createdByName,
        LocalDateTime returnedAt
) {
}

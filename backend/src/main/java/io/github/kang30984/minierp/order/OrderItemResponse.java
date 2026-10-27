package io.github.kang30984.minierp.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Integer lineNo,
        Long productId,
        String sku,
        String productName,
        Integer qty,
        BigDecimal unitPrice,
        BigDecimal amount,
        Integer returnedQty
) {
}

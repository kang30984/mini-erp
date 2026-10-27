package io.github.kang30984.minierp.master.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        Integer stockQty,
        String status,
        LocalDateTime updatedAt
) {
}

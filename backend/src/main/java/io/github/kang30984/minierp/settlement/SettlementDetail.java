package io.github.kang30984.minierp.settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** sourceType: SHIP(출고) / RETURN(반품). 반품은 수량·금액·리베이트가 음수 */
public record SettlementDetail(
        String sourceType,
        String orderNo,
        Integer lineNo,
        String productName,
        Integer qty,
        BigDecimal amount,
        BigDecimal rebateRate,
        BigDecimal rebateAmount,
        LocalDateTime occurredAt
) {
}

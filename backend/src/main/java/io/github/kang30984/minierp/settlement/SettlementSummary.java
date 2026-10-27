package io.github.kang30984.minierp.settlement;

import java.math.BigDecimal;

/** 거래처별 정산 합계. returnAmount는 양수(차감 전 금액), netAmount = 출고 - 반품 */
public record SettlementSummary(
        Long partnerId,
        String partnerCode,
        String partnerName,
        BigDecimal shipAmount,
        BigDecimal returnAmount,
        BigDecimal netAmount,
        BigDecimal rebateAmount
) {
}

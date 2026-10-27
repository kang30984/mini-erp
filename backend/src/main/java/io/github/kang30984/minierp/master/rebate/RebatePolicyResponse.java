package io.github.kang30984.minierp.master.rebate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * state: CURRENT(적용 중), SCHEDULED(예정), ENDED(종료)
 */
public record RebatePolicyResponse(
        Long id,
        String gradeCode,
        String gradeName,
        BigDecimal rebateRate,
        LocalDate validFrom,
        LocalDate validTo,
        String state
) {
}

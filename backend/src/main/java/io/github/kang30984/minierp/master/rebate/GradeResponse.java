package io.github.kang30984.minierp.master.rebate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 등급별 현재 적용 요율과 다음 변경 예정 요율
 */
public record GradeResponse(
        String gradeCode,
        String name,
        BigDecimal currentRate,
        BigDecimal nextRate,
        LocalDate nextValidFrom
) {
}

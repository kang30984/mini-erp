package io.github.kang30984.minierp.master.rebate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RebatePolicyRequest(
        @NotBlank(message = "등급을 선택해 주세요.")
        String gradeCode,

        @NotNull(message = "리베이트율을 입력해 주세요.")
        @DecimalMin(value = "0", message = "리베이트율은 0% 이상이어야 합니다.")
        @DecimalMax(value = "0.5", message = "리베이트율은 50% 이하로 입력해 주세요.")
        @Digits(integer = 1, fraction = 4, message = "리베이트율은 소수점 둘째 자리(0.01%)까지 입력할 수 있습니다.")
        BigDecimal rebateRate,

        @NotNull(message = "적용 시작일을 입력해 주세요.")
        LocalDate validFrom
) {
}

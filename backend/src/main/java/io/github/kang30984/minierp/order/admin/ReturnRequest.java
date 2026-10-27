package io.github.kang30984.minierp.order.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReturnRequest(
        @NotNull(message = "반품할 상품 줄을 선택해 주세요.")
        Integer lineNo,

        @NotNull(message = "반품 수량을 입력해 주세요.")
        @Min(value = 1, message = "반품 수량은 1개 이상이어야 합니다.")
        @Max(value = 999, message = "반품 수량이 너무 많습니다.")
        Integer qty,

        @NotBlank(message = "반품 사유를 입력해 주세요.")
        @Size(max = 500, message = "반품 사유는 500자 이하로 입력해 주세요.")
        String reason
) {
}

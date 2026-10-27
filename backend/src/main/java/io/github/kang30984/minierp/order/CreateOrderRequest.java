package io.github.kang30984.minierp.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 주문 요청에는 상품과 수량만 받는다.
 * 단가는 클라이언트가 보낸 값을 믿지 않고 서버가 DB에서 다시 읽는다.
 */
public record CreateOrderRequest(
        @NotEmpty(message = "주문할 상품을 하나 이상 담아 주세요.")
        @Size(max = 50, message = "한 번에 50개 품목까지 주문할 수 있습니다.")
        List<@Valid @NotNull Line> items
) {

    public record Line(
            @NotNull(message = "상품을 선택해 주세요.")
            Long productId,

            @NotNull(message = "수량을 입력해 주세요.")
            @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
            @Max(value = 999, message = "한 품목은 999개까지 주문할 수 있습니다.")
            Integer qty
    ) {
    }
}

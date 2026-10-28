package io.github.kang30984.minierp.master.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 상품 등록·수정 요청. 수정할 때 sku는 바꾸지 않는다(주문 이력과 연결되는 코드라서).
 *
 * <p>baseStockQty는 수정할 때만 쓴다. 화면이 상품을 불러왔을 때의 재고 수량이다.
 * 재고를 바꾸지 않았으면 재고 칼럼은 건드리지 않고, 바꿨으면 DB 재고가 이 값과 같을 때만 바꾼다.
 * 화면을 열어 둔 사이 승인으로 재고가 줄었는데 예전 숫자로 덮어써서 재고가 되살아나는 것을 막는다.
 */
public record ProductRequest(
        @NotBlank(message = "상품 코드를 입력해 주세요.")
        @Size(max = 30, message = "상품 코드는 30자 이하로 입력해 주세요.")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "상품 코드는 영문 대문자, 숫자, 하이픈만 쓸 수 있습니다.")
        String sku,

        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 200, message = "상품명은 200자 이하로 입력해 주세요.")
        String name,

        @NotBlank(message = "분류를 선택해 주세요.")
        @Pattern(regexp = "NOTEBOOK|DESKTOP|MONITOR|SERVER|STORAGE|NETWORK|SOFTWARE",
                message = "분류 값이 올바르지 않습니다.")
        String category,

        @NotNull(message = "단가를 입력해 주세요.")
        @PositiveOrZero(message = "단가는 0원 이상이어야 합니다.")
        @Digits(integer = 15, fraction = 0, message = "단가는 원 단위 정수로 입력해 주세요.")
        BigDecimal unitPrice,

        @NotNull(message = "재고 수량을 입력해 주세요.")
        @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "재고 수량은 1,000,000 이하로 입력해 주세요.")
        Integer stockQty,

        @NotBlank(message = "판매 상태를 선택해 주세요.")
        @Pattern(regexp = "ON_SALE|STOPPED", message = "판매 상태 값이 올바르지 않습니다.")
        String status,

        @PositiveOrZero(message = "기준 재고 수량은 0 이상이어야 합니다.")
        Integer baseStockQty
) {
}

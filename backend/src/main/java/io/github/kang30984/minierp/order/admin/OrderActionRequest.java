package io.github.kang30984.minierp.order.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 승인·반려·출고·취소 요청.
 * version은 화면이 주문을 불러왔을 때의 값이다. 그 사이 다른 사용자가 처리했으면 충돌로 응답한다.
 */
public record OrderActionRequest(
        @NotNull(message = "주문 버전 정보가 없습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.")
        Integer version,

        @Size(max = 500, message = "사유는 500자 이하로 입력해 주세요.")
        String reason
) {
}

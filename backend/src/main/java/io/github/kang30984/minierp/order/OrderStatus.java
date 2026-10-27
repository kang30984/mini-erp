package io.github.kang30984.minierp.order;

import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.SearchText;

public enum OrderStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELED,
    SHIPPED;

    /** 검색 조건용. 비어 있으면 null(전체), 알 수 없는 값이면 400 */
    public static String parseFilter(String value) {
        String v = SearchText.blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(v).name();
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_STATUS", "주문 상태 값이 올바르지 않습니다: " + v);
        }
    }
}

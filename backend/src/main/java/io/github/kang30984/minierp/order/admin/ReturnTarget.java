package io.github.kang30984.minierp.order.admin;

import java.math.BigDecimal;

/** 반품 대상 주문 줄 (잠금 조회 결과) */
public record ReturnTarget(Long itemId, Long productId, Integer qty, BigDecimal unitPrice) {
}

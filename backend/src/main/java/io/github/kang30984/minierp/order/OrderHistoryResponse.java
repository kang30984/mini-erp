package io.github.kang30984.minierp.order;

import java.time.LocalDateTime;

public record OrderHistoryResponse(
        String fromStatus,
        String toStatus,
        String reason,
        String changedByName,
        LocalDateTime changedAt
) {
}

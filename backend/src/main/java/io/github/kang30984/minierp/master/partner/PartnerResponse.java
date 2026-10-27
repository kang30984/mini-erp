package io.github.kang30984.minierp.master.partner;

import java.time.LocalDateTime;

public record PartnerResponse(
        Long id,
        String code,
        String name,
        String gradeCode,
        String gradeName,
        String status,
        LocalDateTime updatedAt
) {
}

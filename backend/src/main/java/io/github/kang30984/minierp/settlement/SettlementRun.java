package io.github.kang30984.minierp.settlement;

import java.time.LocalDateTime;

public record SettlementRun(
        Long id,
        String yyyymm,
        String runType,
        Integer rowCount,
        Integer elapsedMs,
        String result,
        String runByName,
        LocalDateTime runAt
) {
}

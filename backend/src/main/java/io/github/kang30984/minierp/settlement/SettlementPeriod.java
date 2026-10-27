package io.github.kang30984.minierp.settlement;

import java.time.LocalDateTime;

public record SettlementPeriod(String yyyymm, String status, LocalDateTime closedAt, String closedByName) {
}

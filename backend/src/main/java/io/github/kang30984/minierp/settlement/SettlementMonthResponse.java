package io.github.kang30984.minierp.settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * status: NONE(실행 전) / OPEN(미리보기 상태) / CLOSED(마감)
 * closable: 이 월을 지금 마감할 수 있는지 (지난달까지만, 마감 전, 미리보기 실행 후)
 */
public record SettlementMonthResponse(
        String yyyymm,
        String status,
        LocalDateTime closedAt,
        String closedByName,
        boolean previewable,
        boolean closable,
        List<SettlementSummary> summaries,
        Totals totals,
        List<SettlementRun> runs
) {

    public record Totals(BigDecimal shipAmount, BigDecimal returnAmount, BigDecimal netAmount,
                         BigDecimal rebateAmount) {

        static Totals of(List<SettlementSummary> rows) {
            BigDecimal ship = BigDecimal.ZERO;
            BigDecimal ret = BigDecimal.ZERO;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal rebate = BigDecimal.ZERO;
            for (SettlementSummary r : rows) {
                ship = ship.add(r.shipAmount());
                ret = ret.add(r.returnAmount());
                net = net.add(r.netAmount());
                rebate = rebate.add(r.rebateAmount());
            }
            return new Totals(ship, ret, net, rebate);
        }
    }
}

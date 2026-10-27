package io.github.kang30984.minierp.settlement;

import io.github.kang30984.minierp.auth.AppUser;
import io.github.kang30984.minierp.common.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 정산.
 * - 미리보기: 이번 달까지 실행할 수 있고, 몇 번이든 다시 계산한다.
 * - 마감: 지난달까지만 가능하다. 진행 중인 달을 마감하면 남은 기간의 출고·반품을 받을 수 없게 된다.
 * - 마감 후에는 재실행할 수 없고, DB 트리거가 그 달의 출고·반품 데이터 변경도 막는다.
 */
@Service
@Transactional(readOnly = true)
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int RUN_HISTORY_LIMIT = 10;

    private final SettlementMapper mapper;
    private final Clock clock;

    public SettlementService(SettlementMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public SettlementMonthResponse get(String yyyymm) {
        YearMonth month = parse(yyyymm);
        YearMonth current = YearMonth.now(clock);
        Optional<SettlementPeriod> period = mapper.findPeriod(yyyymm);
        String status = period.map(SettlementPeriod::status).orElse("NONE");

        List<SettlementSummary> summaries = mapper.findSummaries(yyyymm);
        boolean previewable = !"CLOSED".equals(status) && !month.isAfter(current);
        boolean closable = "OPEN".equals(status) && month.isBefore(current);

        return new SettlementMonthResponse(yyyymm, status,
                period.map(SettlementPeriod::closedAt).orElse(null),
                period.map(SettlementPeriod::closedByName).orElse(null),
                previewable, closable, summaries, SettlementMonthResponse.Totals.of(summaries),
                mapper.findRuns(yyyymm, RUN_HISTORY_LIMIT));
    }

    public List<SettlementDetail> details(String yyyymm, long partnerId) {
        parse(yyyymm);
        return mapper.findDetails(yyyymm, partnerId);
    }

    @Transactional
    public SettlementMonthResponse preview(AppUser admin, String yyyymm) {
        YearMonth month = parse(yyyymm);
        if (month.isAfter(YearMonth.now(clock))) {
            throw BusinessException.badRequest("FUTURE_MONTH", "아직 오지 않은 달은 정산할 수 없습니다.");
        }
        ensureNotClosed(yyyymm);
        run(admin, yyyymm, false);
        return get(yyyymm);
    }

    /**
     * 마감도 프로시저 안에서 한 번 더 계산한 뒤 닫는다.
     * 미리보기를 본 뒤에 들어온 반품이 있어도 마감 결과에 빠지지 않는다.
     */
    @Transactional
    public SettlementMonthResponse close(AppUser admin, String yyyymm) {
        YearMonth month = parse(yyyymm);
        if (!month.isBefore(YearMonth.now(clock))) {
            throw BusinessException.badRequest("MONTH_IN_PROGRESS",
                    "진행 중인 달은 마감할 수 없습니다. 지난달까지만 마감할 수 있습니다.");
        }
        Optional<SettlementPeriod> period = mapper.findPeriod(yyyymm);
        if (period.isEmpty()) {
            throw BusinessException.badRequest("PREVIEW_REQUIRED", "미리보기로 정산 결과를 먼저 확인해 주세요.");
        }
        ensureNotClosed(yyyymm);
        run(admin, yyyymm, true);
        return get(yyyymm);
    }

    /** 엑셀에서 바로 열 수 있도록 UTF-8 BOM을 붙인 CSV */
    public byte[] csv(String yyyymm) {
        parse(yyyymm);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("정산월,거래처코드,거래처명,출고금액,반품금액,순매출,리베이트\r\n");
        List<SettlementSummary> rows = mapper.findSummaries(yyyymm);
        for (SettlementSummary r : rows) {
            sb.append(yyyymm).append(',')
                    .append(csvText(r.partnerCode())).append(',')
                    .append(csvText(r.partnerName())).append(',')
                    .append(plain(r.shipAmount())).append(',')
                    .append(plain(r.returnAmount())).append(',')
                    .append(plain(r.netAmount())).append(',')
                    .append(plain(r.rebateAmount())).append("\r\n");
        }
        SettlementMonthResponse.Totals t = SettlementMonthResponse.Totals.of(rows);
        sb.append(yyyymm).append(",합계,,")
                .append(plain(t.shipAmount())).append(',')
                .append(plain(t.returnAmount())).append(',')
                .append(plain(t.netAmount())).append(',')
                .append(plain(t.rebateAmount())).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void run(AppUser admin, String yyyymm, boolean close) {
        try {
            mapper.runSettlement(yyyymm, admin.id(), close);
        } catch (DataAccessException e) {
            // 프로시저가 RAISE EXCEPTION으로 중단한 경우. 동시에 마감된 경우나 리베이트 정책이 빠진 경우 등
            String reason = rootMessage(e);
            log.warn("정산 실행 실패 yyyymm={} close={} reason={}", yyyymm, close, reason);
            throw BusinessException.conflict("SETTLEMENT_FAILED", "정산을 실행하지 못했습니다. " + reason);
        }
    }

    private void ensureNotClosed(String yyyymm) {
        mapper.findPeriod(yyyymm)
                .filter(p -> "CLOSED".equals(p.status()))
                .ifPresent(p -> {
                    throw BusinessException.conflict("ALREADY_CLOSED", yyyymm + " 정산은 이미 마감되었습니다.");
                });
    }

    private static YearMonth parse(String yyyymm) {
        try {
            if (yyyymm == null || !yyyymm.matches("\\d{6}")) {
                throw new DateTimeParseException("형식 오류", String.valueOf(yyyymm), 0);
            }
            return YearMonth.parse(yyyymm, YYYYMM);
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("INVALID_MONTH", "정산월은 202609처럼 연도 4자리와 월 2자리로 입력해 주세요.");
        }
    }

    /** PostgreSQL 에러 메시지에서 "ERROR: " 뒤의 본문만 꺼낸다 */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = String.valueOf(root.getMessage());
        int start = message.indexOf("ERROR: ");
        if (start >= 0) {
            message = message.substring(start + 7);
        }
        int lineBreak = message.indexOf('\n');
        return lineBreak >= 0 ? message.substring(0, lineBreak).trim() : message.trim();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private static String csvText(String value) {
        String v = value == null ? "" : value;
        // 엑셀이 =, +, -, @로 시작하는 값을 수식으로 실행하지 않도록 앞에 작은따옴표를 붙인다 (CSV 인젝션 방지)
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}

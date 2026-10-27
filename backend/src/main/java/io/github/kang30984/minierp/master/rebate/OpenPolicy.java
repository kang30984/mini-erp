package io.github.kang30984.minierp.master.rebate;

import java.time.LocalDate;

/**
 * 종료일이 없는(현재 마지막) 정책
 */
public record OpenPolicy(Long id, LocalDate validFrom) {
}

package io.github.kang30984.minierp.auth;

import java.io.Serializable;

/**
 * 로그인 사용자 정보. 세션에 저장되므로 Serializable.
 * enabled: 관리자는 항상 true, 거래처 사용자는 소속 거래처가 ACTIVE일 때만 true
 */
public record AppUser(
        Long id,
        String loginId,
        String passwordHash,
        String name,
        String role,
        Long partnerId,
        String partnerName,
        boolean enabled
) implements Serializable {
}

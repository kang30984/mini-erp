package io.github.kang30984.minierp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인 실패가 계정 상태와 상관없이 같은 비용(비밀번호 비교 1회)을 치르는지 확인한다.
 * 응답 시간을 직접 재면 환경에 따라 흔들리므로, 비밀번호 비교 횟수로 확인한다.
 * DB가 필요 없어서 Spring 컨텍스트 없이 돈다.
 */
class LoginProviderTest {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);
    private static final String PASSWORD = "correct-password";

    private final CountingEncoder encoder = new CountingEncoder();

    @Test
    @DisplayName("거래 중지 계정도 비밀번호를 먼저 비교한다. 틀린 비밀번호면 일반 비밀번호 오류로 실패한다")
    void disabledAccountWithWrongPasswordStillComparesPassword() {
        DaoAuthenticationProvider provider = providerReturning(user(false));

        assertThatThrownBy(() -> provider.authenticate(login("wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(encoder.matchesCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("거래 중지 계정은 비밀번호가 맞을 때만 상태 때문에 거절된다")
    void disabledAccountWithCorrectPasswordIsRejectedAfterComparison() {
        DaoAuthenticationProvider provider = providerReturning(user(false));

        assertThatThrownBy(() -> provider.authenticate(login(PASSWORD)))
                .isInstanceOf(DisabledException.class);
        assertThat(encoder.matchesCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 비교를 한 번 한다")
    void unknownUserAlsoComparesPassword() {
        DaoAuthenticationProvider provider = SecurityConfig.loginProvider(username -> {
            throw new UsernameNotFoundException(username);
        }, encoder);

        assertThatThrownBy(() -> provider.authenticate(login("any")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(encoder.matchesCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("정상 계정은 그대로 로그인된다")
    void activeAccountLogsIn() {
        DaoAuthenticationProvider provider = providerReturning(user(true));

        assertThat(provider.authenticate(login(PASSWORD)).isAuthenticated()).isTrue();
    }

    private DaoAuthenticationProvider providerReturning(UserDetails user) {
        return SecurityConfig.loginProvider(username -> user, encoder);
    }

    private static UserDetails user(boolean enabled) {
        return User.withUsername("partner9")
                .password(BCRYPT.encode(PASSWORD))
                .roles("PARTNER")
                .disabled(!enabled)
                .build();
    }

    private static UsernamePasswordAuthenticationToken login(String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated("partner9", password);
    }

    /** 비밀번호 비교 횟수를 세는 인코더 */
    private static final class CountingEncoder implements PasswordEncoder {

        final AtomicInteger matchesCalls = new AtomicInteger();

        @Override
        public String encode(CharSequence rawPassword) {
            return BCRYPT.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchesCalls.incrementAndGet();
            return BCRYPT.matches(rawPassword, encodedPassword);
        }
    }
}

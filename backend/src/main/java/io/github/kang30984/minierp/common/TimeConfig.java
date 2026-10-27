package io.github.kang30984.minierp.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 업무 기준 시간대는 한국 시간으로 고정한다.
 * 서버가 UTC로 떠 있어도 "오늘", "이번 달"이 한국 기준으로 계산되도록
 * 날짜 계산은 이 Clock을 주입받아서 한다. (DB 세션 시간대는 application.yml에서 맞춘다)
 */
@Configuration
public class TimeConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}

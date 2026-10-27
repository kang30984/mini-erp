package io.github.kang30984.minierp.master;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kang30984.minierp.IntegrationTestSupport;
import io.github.kang30984.minierp.common.TimeConfig;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class RebatePolicyIntegrationTest extends IntegrationTestSupport {

    private final LocalDate today = LocalDate.now(TimeConfig.BUSINESS_ZONE);

    private Map<String, Object> policy(String grade, String rate, LocalDate from) {
        return Map.of("gradeCode", grade, "rebateRate", rate, "validFrom", from.toString());
    }

    @Test
    @DisplayName("요율을 변경하면 기존 정책은 새 시작일로 종료되고 새 정책은 예정 상태가 된다")
    void registerClosesPrevious() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        LocalDate from = today.plusDays(10);

        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(policy("GOLD", "0.0250", from))))
                .andExpect(status().isCreated())
                // 최신 시작일 순으로 정렬: [0] 새 정책, [1] 기존 정책
                .andExpect(jsonPath("$[0].rebateRate").value(0.025))
                .andExpect(jsonPath("$[0].state").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].validTo").doesNotExist())
                .andExpect(jsonPath("$[1].validTo").value(from.toString()))
                .andExpect(jsonPath("$[1].state").value("CURRENT"));

        mvc.perform(get("/api/admin/grades").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.gradeCode == 'GOLD')].currentRate").value(0.02))
                .andExpect(jsonPath("$[?(@.gradeCode == 'GOLD')].nextRate").value(0.025));
    }

    @Test
    @DisplayName("오늘 날짜로는 요율을 바꿀 수 없다")
    void todayIsRejected() throws Exception {
        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(policy("GOLD", "0.0250", today))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALID_FROM"));
    }

    @Test
    @DisplayName("예정된 정책보다 앞선 날짜로는 등록할 수 없다")
    void earlierThanScheduledIsRejected() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(policy("SILVER", "0.0150", today.plusDays(20)))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(policy("SILVER", "0.0120", today.plusDays(5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VALID_FROM"));
    }

    @Test
    @DisplayName("리베이트율은 소수점 넷째 자리까지만 받는다")
    void rateScaleLimited() throws Exception {
        mvc.perform(post("/api/admin/rebate-policies").with(csrf()).session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(policy("GOLD", "0.02555", today.plusDays(3)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

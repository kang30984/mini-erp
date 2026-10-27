package io.github.kang30984.minierp.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kang30984.minierp.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("관리자로 로그인하면 세션으로 내 정보를 조회할 수 있다")
    void adminLoginAndMe() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("거래처 사용자는 소속 거래처 정보가 함께 내려온다")
    void partnerLoginIncludesPartner() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("partner1", "partner1234!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PARTNER"))
                .andExpect(jsonPath("$.partnerName").value("한빛시스템"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401과 공통 메시지를 응답한다")
    void wrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 오류와 같은 응답을 준다")
    void unknownUser() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody", "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("CSRF 토큰 없이 로그인하면 403")
    void loginWithoutCsrf() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin", "admin1234!")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("아이디를 비우면 400")
    void blankLoginId() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "admin1234!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("로그인하지 않으면 내 정보 조회는 401")
    void meWithoutLogin() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("거래처 사용자는 관리자 API에 접근할 수 없다")
    void partnerCannotAccessAdminApi() throws Exception {
        MockHttpSession session = loginAs("partner1");

        mvc.perform(get("/api/admin/orders").session(session))
                .andExpect(status().isForbidden());
    }

    private String body(String loginId, String password) {
        return """
                {"loginId": "%s", "password": "%s"}
                """.formatted(loginId, password);
    }
}

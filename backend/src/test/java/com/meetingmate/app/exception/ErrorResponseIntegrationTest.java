package com.meetingmate.app.exception;

import com.meetingmate.app.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the HTTP status of common error cases with the real security filter chain.
 * Before the fix, every case below except validation returned 500 (or 302 for auth).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseIntegrationTest {

    @Autowired MockMvc mvc;
    @MockitoBean RecommendationService recommendationService;

    @Test
    void unknownPathIs404() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(user("u")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(delete("/api/recommend").param("keyword", "x").with(user("u")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedJsonIs400AndWrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void missingRequiredParamIs400() throws Exception {
        mvc.perform(get("/api/recommend").with(user("u")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedApiCallIs401JsonNotRedirect() throws Exception {
        mvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oauthLoginStartStillRedirects() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void unexpectedExceptionIs500AndIsLogged(CapturedOutput output) throws Exception {
        when(recommendationService.recommendByKeyword(any(), any(), any(), any(), any()))
                .thenThrow(new NullPointerException("boom"));

        mvc.perform(get("/api/recommend").param("keyword", "홍대").with(user("u")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        // Previously the catch-all handler swallowed the stack trace entirely.
        assertThat(output).contains("Unhandled exception").contains("NullPointerException: boom");
    }
}

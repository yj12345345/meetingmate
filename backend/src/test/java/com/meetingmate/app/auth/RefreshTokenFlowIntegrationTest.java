package com.meetingmate.app.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login -> refresh (rotation) -> logout over HTTP, checking the cookie attributes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenFlowIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void loginRefreshLogout() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"flow@test.local\",\"password\":\"flow-pass-1\",\"nickname\":\"flow\"}"));

        MockHttpServletResponse login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@test.local\",\"password\":\"flow-pass-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse();
        String setCookie = login.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("REFRESH_TOKEN=")
                .contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth");
        Cookie first = login.getCookie("REFRESH_TOKEN");

        MockHttpServletResponse refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse();
        Cookie second = refreshed.getCookie("REFRESH_TOKEN");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        MockHttpServletResponse logout = mvc.perform(post("/api/auth/logout").cookie(second))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(logout.getHeader("Set-Cookie")).contains("Max-Age=0");

        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }
}

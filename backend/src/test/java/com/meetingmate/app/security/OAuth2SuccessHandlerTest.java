package com.meetingmate.app.security;

import com.meetingmate.app.auth.RefreshTokenCookie;
import com.meetingmate.app.auth.RefreshTokenService;
import com.meetingmate.app.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    @Test
    void redirectCarriesNoTokenAndSetsHttpOnlyRefreshCookie() throws Exception {
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        when(refreshTokens.issue(7L)).thenReturn(new RefreshTokenService.Issued("raw-refresh", 7L, Duration.ofDays(14)));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(refreshTokens, new RefreshTokenCookie(true));
        ReflectionTestUtils.setField(handler, "frontendRedirectUrl", "https://meetingmate.duckdns.org/oauth/callback");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(new UserPrincipal(User.builder().id(7L).build()), null));

        // The access token used to be appended as ?token=..., leaking into history and logs.
        assertThat(response.getRedirectedUrl()).isEqualTo("https://meetingmate.duckdns.org/oauth/callback");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("REFRESH_TOKEN=raw-refresh").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/api/auth");
    }
}

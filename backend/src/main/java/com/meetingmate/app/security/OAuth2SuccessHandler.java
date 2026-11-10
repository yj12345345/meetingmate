package com.meetingmate.app.security;

import com.meetingmate.app.auth.RefreshTokenCookie;
import com.meetingmate.app.auth.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * After Kakao/Google login: set the refresh-token cookie and redirect to the frontend.
 *
 * <p>The access token used to be appended to the redirect URL ({@code ?token=...}),
 * which leaks it into browser history, Nginx access logs and Referer headers. Now the
 * URL carries nothing; the frontend callback page calls POST /api/auth/refresh, which
 * reads the HttpOnly cookie and returns an access token in the response body.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookie refreshTokenCookie;

    @Value("${app.frontend-redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        RefreshTokenService.Issued refresh = refreshTokenService.issue(principal.getId());
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookie.create(refresh.rawToken(), refresh.ttl()).toString());
        response.sendRedirect(frontendRedirectUrl);
    }
}

package com.meetingmate.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Unauthenticated /api/** requests get 401 + JSON instead of the oauth2Login default
 * (302 redirect to /login). An XHR client cannot act on a redirect to an HTML login
 * page, so an expired token used to surface as a confusing failure on the frontend.
 * A clear 401 is also what the frontend's refresh-token interceptor keys on.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY =
            "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\",\"data\":null}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}

package com.meetingmate.app.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * The refresh token lives only in this cookie:
 * HttpOnly (JavaScript, hence XSS, cannot read it), Secure (HTTPS only; disabled for
 * local http), SameSite=Strict (never sent on cross-site requests, which is what makes
 * the cookie-authenticated /refresh endpoint safe from CSRF), Path=/api/auth (not sent
 * with ordinary API calls).
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "REFRESH_TOKEN";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshTokenCookie(@Value("${auth.refresh-token.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String rawToken, Duration ttl) {
        return base(rawToken).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(c -> NAME.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
    }
}

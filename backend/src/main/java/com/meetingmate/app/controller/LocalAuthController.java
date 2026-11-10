package com.meetingmate.app.controller;

import com.meetingmate.app.auth.RefreshTokenCookie;
import com.meetingmate.app.auth.RefreshTokenService;
import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.auth.LocalAuthService;
import com.meetingmate.app.dto.auth.LocalLoginRequest;
import com.meetingmate.app.dto.auth.LocalSignupRequest;
import com.meetingmate.app.dto.auth.TokenResponse;
import com.meetingmate.app.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class LocalAuthController {

    private final LocalAuthService localAuthService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody LocalSignupRequest req) {
        localAuthService.signup(req);
        return ResponseEntity.ok(ApiResponse.success("SIGNUP_SUCCESS", "회원가입이 완료되었습니다.", null));
    }

    /** Access token in the body (15 min), refresh token only in an HttpOnly cookie (14 days). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LocalLoginRequest req) {
        Long userId = localAuthService.authenticate(req);
        RefreshTokenService.Issued refresh = refreshTokenService.issue(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(refresh.rawToken(), refresh.ttl()).toString())
                .body(ApiResponse.success("LOGIN_SUCCESS", "로그인이 완료되었습니다.",
                        new TokenResponse(jwtProvider.createAccessToken(userId))));
    }

    /**
     * Rotates the refresh cookie and returns a new access token. Called by the frontend
     * when an API call gets 401, and once after the OAuth redirect (which no longer
     * carries the token in the URL). Invalid tokens -> 401 + cookie cleared.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request) {
        RefreshTokenService.Issued rotated = refreshTokenService.rotate(refreshTokenCookie.read(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(rotated.rawToken(), rotated.ttl()).toString())
                .body(ApiResponse.success("TOKEN_REFRESHED", "토큰이 갱신되었습니다.",
                        new TokenResponse(jwtProvider.createAccessToken(rotated.userId()))));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        refreshTokenService.revokeFamilyOf(refreshTokenCookie.read(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .build();
    }
}

package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.auth.LocalAuthService;
import com.meetingmate.app.dto.auth.LocalLoginRequest;
import com.meetingmate.app.dto.auth.LocalSignupRequest;
import com.meetingmate.app.dto.auth.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody LocalSignupRequest req) {
        localAuthService.signup(req);
        return ResponseEntity.ok(ApiResponse.success("SIGNUP_SUCCESS", "회원가입이 완료되었습니다.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LocalLoginRequest req) {
        return ResponseEntity.ok(
                ApiResponse.success("LOGIN_SUCCESS", "로그인이 완료되었습니다.", localAuthService.login(req))
        );
    }
}

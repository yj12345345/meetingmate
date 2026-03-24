package com.meetingmate.app.dto.auth;

import com.meetingmate.app.domain.user.AuthProvider;
import com.meetingmate.app.domain.user.User;
import com.meetingmate.app.repository.UserRepository;
import com.meetingmate.app.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider; // ⭐ 기존 JWT 클래스

    public void signup(LocalSignupRequest req) {
        if (userRepository.existsByEmailAndProvider(req.getEmail(), AuthProvider.LOCAL)) {
            throw new IllegalArgumentException("이미 존재하는 이메일");
        }

        User user = User.builder()
                .provider(AuthProvider.LOCAL)
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname())
                .build();

        userRepository.save(user);
    }

    public TokenResponse login(LocalLoginRequest req) {
        User user = userRepository
                .findByEmailAndProvider(req.getEmail(), AuthProvider.LOCAL)
                .orElseThrow(() -> new IllegalArgumentException("로그인 실패"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("로그인 실패");
        }

        // ⭐ 기존 OAuth 로그인과 동일한 JWT 발급
        String token = jwtProvider.createAccessToken(user.getId());

        return new TokenResponse(token);
    }
}
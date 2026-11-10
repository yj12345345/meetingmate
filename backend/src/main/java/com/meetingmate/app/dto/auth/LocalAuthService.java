package com.meetingmate.app.dto.auth;

import com.meetingmate.app.domain.user.AuthProvider;
import com.meetingmate.app.domain.user.User;
import com.meetingmate.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    /** Verifies email/password and returns the user id; tokens are issued by the caller. */
    public Long authenticate(LocalLoginRequest req) {
        User user = userRepository
                .findByEmailAndProvider(req.getEmail(), AuthProvider.LOCAL)
                .orElseThrow(() -> new IllegalArgumentException("로그인 실패"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("로그인 실패");
        }

        return user.getId();
    }
}
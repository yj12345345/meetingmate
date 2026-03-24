package com.meetingmate.app.repository;

import com.meetingmate.app.domain.user.AuthProvider;
import com.meetingmate.app.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 로컬 로그인용
    Optional<User> findByEmailAndProvider(String email, AuthProvider provider);
    boolean existsByEmailAndProvider(String email, AuthProvider provider);
    Optional<User> findByEmail(String email);

    // OAuth2 로그인용
    Optional<User> findByProviderIdAndProvider(String providerId, AuthProvider provider);
}

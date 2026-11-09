package com.meetingmate.app.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Login paths: local login (email + provider) and Kakao login (provider_id + provider).
@Table(indexes = {
        @Index(name = "idx_user_email_provider", columnList = "email, provider"),
        @Index(name = "idx_user_provider_id_provider", columnList = "provider_id, provider")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerId;
    private String email;
    private String password;

    private String nickname;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

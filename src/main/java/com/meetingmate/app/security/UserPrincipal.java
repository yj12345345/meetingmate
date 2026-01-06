package com.meetingmate.app.security;

import com.meetingmate.app.domain.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class UserPrincipal implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    public UserPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    /**
     * ROLE 반환
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * OAuth2 프로필 정보
     */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Spring Security에서 사용자 이름으로 사용되는 필드
     */
    @Override
    public String getName() {
        return user.getNickname();
    }

    /**
     * 우리가 필요로 하는 사용자 ID (User 엔티티의 PK)
     */
    public Long getId() {
        return user.getId();
    }
}
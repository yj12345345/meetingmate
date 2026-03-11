package com.meetingmate.app.domain.user.dto;

import com.meetingmate.app.domain.user.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserMeResponse {

    private final Long id;
    private final String email;
    private final String nickname;
    private final String provider;
    private final LocalDateTime createdAt;

    public UserMeResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.provider = user.getProvider().name();
        this.createdAt = user.getCreatedAt();
    }
}

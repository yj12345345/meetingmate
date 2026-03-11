package com.meetingmate.app.dto.group;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JoinResponse {
    private Long groupId;
    private String groupName;
}
package com.meetingmate.app.dto.group;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupDetailResponse {
    private Long groupId;
    private String groupName;
    private String description;
    private Long hostUserId;
    private long memberCount;
}

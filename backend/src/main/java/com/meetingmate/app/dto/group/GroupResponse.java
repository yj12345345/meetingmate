package com.meetingmate.app.dto.group;

import com.meetingmate.app.domain.group.Group;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupResponse {

    private Long id;
    private String name;
    private String description;
    private String inviteCode;

    public static GroupResponse from(Group group) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .inviteCode(group.getInviteCode())
                .build();
    }
}
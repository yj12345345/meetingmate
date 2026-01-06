package com.meetingmate.app.dto.group;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyGroupResponse {
    private Long groupId;
    private String groupName;
}
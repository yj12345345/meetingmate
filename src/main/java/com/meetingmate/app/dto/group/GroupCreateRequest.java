package com.meetingmate.app.dto.group;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupCreateRequest {

    private String name;
    private String description;
}
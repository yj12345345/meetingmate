package com.meetingmate.app.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class GroupJoinRequest {

    @NotBlank(message = "초대 코드는 필수입니다.")
    @Size(min = 6, max = 6, message = "초대 코드는 6자리여야 합니다.")
    private String inviteCode;
}

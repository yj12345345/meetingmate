package com.meetingmate.app.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupCreateRequest {

    @NotBlank(message = "모임 이름은 필수입니다.")
    @Size(max = 50, message = "모임 이름은 50자 이하여야 합니다.")
    private String name;

    @Size(max = 255, message = "모임 설명은 255자 이하여야 합니다.")
    private String description;
}

package com.meetingmate.app.dto.recommendation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaceRecommendRequest {

    @NotBlank(message = "모임 유형은 필수입니다.")
    @Size(max = 30, message = "모임 유형은 30자 이하여야 합니다.")
    private String meetingType;     // 데이트, 동창회, 회의

    @NotBlank(message = "카테고리는 필수입니다.")
    @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
    private String category;        // FOOD / CAFE / ACTIVITY

    @NotEmpty(message = "선호 조건은 최소 1개 이상이어야 합니다.")
    @Size(max = 10, message = "선호 조건은 최대 10개까지 입력할 수 있습니다.")
    private List<@NotBlank(message = "선호 조건은 비어 있을 수 없습니다.") String> preferences;
}

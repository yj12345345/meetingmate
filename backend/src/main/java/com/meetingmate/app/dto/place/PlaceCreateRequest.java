package com.meetingmate.app.dto.place;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PlaceCreateRequest {

    @NotBlank(message = "장소 이름은 필수입니다.")
    @Size(max = 100, message = "장소 이름은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "장소 카테고리는 필수입니다.")
    @Size(max = 30, message = "장소 카테고리는 30자 이하여야 합니다.")
    private String category;

    @NotNull(message = "위도는 필수입니다.")
    private Double lat;

    @NotNull(message = "경도는 필수입니다.")
    private Double lng;
}

package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlaceRecommendResponse {
    private String name;
    private String reason;
}
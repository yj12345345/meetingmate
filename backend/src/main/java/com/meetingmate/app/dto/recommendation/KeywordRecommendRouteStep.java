package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KeywordRecommendRouteStep {
    private Integer order;
    private String category;
    private String placeName;
    private String address;
    private String reason;
    private String mapQuery;
}

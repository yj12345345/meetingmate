package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KeywordRecommendRoute {
    private String title;
    private String summary;
    private String fitReason;
    private List<KeywordRecommendRouteStep> steps;
}

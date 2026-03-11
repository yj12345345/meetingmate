package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SelectedRouteRecommendResult {
    private List<KeywordRecommendResponse> selectedPlaces;
    private List<KeywordRecommendRoute> routes;
    private String source;
    private String warning;
}

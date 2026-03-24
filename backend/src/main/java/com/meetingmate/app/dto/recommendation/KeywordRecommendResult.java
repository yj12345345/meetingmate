package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KeywordRecommendResult {
    private List<KeywordRecommendResponse> recommendations;
    private List<KeywordRecommendCategory> categories;
    private List<KeywordRecommendRoute> routes;
    private String source;
    private String warning;
}

package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KeywordRecommendCategory {
    private String key;
    private String title;
    private String description;
    private List<KeywordRecommendResponse> places;
}

package com.meetingmate.app.dto.recommendation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KeywordRecommendResponse {
    private String category;
    private String name;
    private String address;
    private String reason;
    private List<String> tags;
    private String mapQuery;
}

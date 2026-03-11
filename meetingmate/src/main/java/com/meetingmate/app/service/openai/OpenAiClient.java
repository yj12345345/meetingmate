package com.meetingmate.app.service.openai;

import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRoute;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import java.util.List;

public interface OpenAiClient {
    List<PlaceRecommendResponse> recommend(String prompt);
    KeywordRecommendResult recommendKeywords(String prompt);
    List<KeywordRecommendRoute> recommendSelectedRoutes(String prompt);
}

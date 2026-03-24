package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.SelectedRouteRecommendRequest;
import com.meetingmate.app.dto.recommendation.SelectedRouteRecommendResult;
import com.meetingmate.app.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendationService recommendationService;

    @GetMapping("/api/recommend")
    public ResponseEntity<ApiResponse<KeywordRecommendResult>> recommendByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "meetingType", required = false) String meetingType,
            @RequestParam(value = "mood", required = false) String mood,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "planHint", required = false) String planHint
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "KEYWORD_RECOMMENDATIONS_FETCHED",
                        "키워드 기반 추천 결과를 조회했습니다.",
                        recommendationService.recommendByKeyword(keyword, meetingType, mood, location, planHint)
                )
        );
    }

    @PostMapping("/api/recommend/selected-routes")
    public ResponseEntity<ApiResponse<SelectedRouteRecommendResult>> recommendSelectedRoutes(
            @RequestBody SelectedRouteRecommendRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "SELECTED_ROUTE_RECOMMENDATIONS_FETCHED",
                        "선택한 장소 기반 코스 추천 결과를 조회했습니다.",
                        recommendationService.recommendRoutesFromSelections(request)
                )
        );
    }
}

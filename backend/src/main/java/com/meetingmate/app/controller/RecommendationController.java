package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.recommendation.PlaceRecommendRequest;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import jakarta.validation.Valid;
import com.meetingmate.app.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<PlaceRecommendResponse>>> recommend(
            @Valid @RequestBody PlaceRecommendRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("RECOMMENDATIONS_FETCHED", "장소 추천 결과를 조회했습니다.", recommendationService.recommend(request))
        );
    }
}

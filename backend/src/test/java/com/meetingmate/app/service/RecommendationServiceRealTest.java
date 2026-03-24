package com.meetingmate.app.service;

import com.meetingmate.app.dto.recommendation.PlaceRecommendRequest;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")   // 실제 OpenAI 키 사용하는 프로필
@Disabled("Manual integration test that requires local DB and OpenAI credentials")
class RecommendationServiceRealTest {

    @Autowired
    private RecommendationService recommendationService;

    @Test
    void 실제_OpenAI_호출로_추천이_생성된다() {
        // given
        PlaceRecommendRequest req = new PlaceRecommendRequest(
                "친구모임",
                "CAFE",
                List.of("조용한", "분위기 좋은")
        );

        // when
        List<PlaceRecommendResponse> result =
                recommendationService.recommend(req);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getName()).isNotBlank();
        assertThat(result.get(0).getReason()).isNotBlank();
    }
}

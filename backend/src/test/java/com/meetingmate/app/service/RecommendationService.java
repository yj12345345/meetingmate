package com.meetingmate.app.service;

import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.KeywordRecommendCategory;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRoute;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRouteStep;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import com.meetingmate.app.dto.recommendation.PlaceRecommendRequest;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResponse;
import com.meetingmate.app.service.openai.OpenAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    // 🔑 외부 의존성 완전 차단
    @MockitoBean
    private OpenAiClient openAiClient;

    @Test
    void 장소_추천_서비스가_OpenAI_결과를_정상_가공한다() {
        // given
        PlaceRecommendRequest req = PlaceRecommendRequest.builder()
                .meetingType("친구모임")
                .category("CAFE")
                .preferences(List.of("조용한", "대화하기 좋은"))
                .build();

        List<PlaceRecommendResponse> aiResult = List.of(
                PlaceRecommendResponse.builder()
                        .name("카페 A")
                        .reason("조용하고 대화하기 좋아요")
                        .build(),
                PlaceRecommendResponse.builder()
                        .name("카페 B")
                        .reason("좌석이 넓어요")
                        .build()
        );

        when(openAiClient.recommend(anyString()))
                .thenReturn(aiResult);

        // when
        List<PlaceRecommendResponse> result =
                recommendationService.recommend(req);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("카페 A");
    }

    @Test
    void 키워드_추천이_OpenAI_응답을_우선_사용한다() {
        KeywordRecommendResponse cafe = KeywordRecommendResponse.builder()
                .category("CAFE")
                .name("성수 브런치 카페")
                .address("서울 성동구 성수동")
                .reason("친구 모임에 어울리는 밝은 분위기입니다.")
                .tags(List.of("#브런치", "#대화하기좋음"))
                .mapQuery("성수 브런치 카페")
                .build();

        when(openAiClient.recommendKeywords(anyString()))
                .thenReturn(
                        KeywordRecommendResult.builder()
                                .recommendations(List.of(cafe))
                                .categories(List.of(
                                        KeywordRecommendCategory.builder()
                                                .key("CAFE")
                                                .title("카페")
                                                .description("대화하기 좋은 카페 후보입니다.")
                                                .places(List.of(cafe))
                                                .build()
                                ))
                                .routes(List.of(
                                        KeywordRecommendRoute.builder()
                                                .title("성수 가벼운 코스")
                                                .summary("카페에서 시작하는 코스입니다.")
                                                .fitReason("친구와 가볍게 만나기 좋습니다.")
                                                .steps(List.of(
                                                        KeywordRecommendRouteStep.builder()
                                                                .order(1)
                                                                .category("CAFE")
                                                                .placeName("성수 브런치 카페")
                                                                .address("서울 성동구 성수동")
                                                                .reason("브런치로 시작하기 좋습니다.")
                                                                .mapQuery("성수 브런치 카페")
                                                                .build()
                                                ))
                                                .build()
                                ))
                                .build()
                );

        KeywordRecommendResult result =
                recommendationService.recommendByKeyword("성수", "친구", "밝은", "성수", null);

        assertThat(result.getSource()).isEqualTo("AI");
        assertThat(result.getRecommendations()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.getRecommendations())
                .extracting(KeywordRecommendResponse::getName)
                .contains("성수 브런치 카페");
        assertThat(result.getCategories()).hasSize(3);
        assertThat(result.getRoutes()).isNotEmpty();
    }
}

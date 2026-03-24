package com.meetingmate.app.controller;

import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.dto.recommendation.KeywordRecommendCategory;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResponse;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRoute;
import com.meetingmate.app.dto.recommendation.KeywordRecommendRouteStep;
import com.meetingmate.app.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class RecommendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void 키워드_추천_API가_상태와_경고를_함께_응답한다() throws Exception {
        when(recommendationService.recommendByKeyword("잠실", "직장 동료", "활동적인", "잠실", null))
                .thenReturn(
                        KeywordRecommendResult.builder()
                                .source("FALLBACK")
                                .warning("OpenAI 추천 대신 기본 추천을 제공했습니다.")
                                .categories(
                                        List.of(
                                                KeywordRecommendCategory.builder()
                                                        .key("RESTAURANT")
                                                        .title("식당")
                                                        .description("식사 시작점 후보입니다.")
                                                        .places(
                                                                List.of(
                                                                        KeywordRecommendResponse.builder()
                                                                                .category("RESTAURANT")
                                                                                .name("잠실 추천 식당 1")
                                                                                .address("서울 송파구 잠실동")
                                                                                .reason("기본 추천입니다.")
                                                                                .tags(List.of("#식사하기좋음"))
                                                                                .mapQuery("잠실 추천 식당 1")
                                                                                .build()
                                                                )
                                                        )
                                                        .build()
                                        )
                                )
                                .routes(
                                        List.of(
                                                KeywordRecommendRoute.builder()
                                                        .title("기본 코스")
                                                        .summary("식사 후 카페로 이동하는 코스입니다.")
                                                        .fitReason("무난하게 만나기 좋습니다.")
                                                        .steps(
                                                                List.of(
                                                                        KeywordRecommendRouteStep.builder()
                                                                                .order(1)
                                                                                .category("RESTAURANT")
                                                                                .placeName("잠실 추천 식당 1")
                                                                                .address("서울 송파구 잠실동")
                                                                                .reason("식사로 시작하기 좋습니다.")
                                                                                .mapQuery("잠실 추천 식당 1")
                                                                                .build()
                                                                )
                                                        )
                                                        .build()
                                        )
                                )
                                .recommendations(
                                        List.of(
                                                KeywordRecommendResponse.builder()
                                                        .category("RESTAURANT")
                                                        .name("잠실 추천 장소 1")
                                                        .address("서울 송파구 잠실동")
                                                        .reason("기본 추천입니다.")
                                                        .tags(List.of("#식사하기좋음"))
                                                        .mapQuery("잠실 추천 장소 1")
                                                        .build()
                                        )
                                )
                                .build()
                );

        mockMvc.perform(
                        get("/api/recommend")
                                .param("keyword", "잠실")
                                .param("meetingType", "직장 동료")
                                .param("mood", "활동적인")
                                .param("location", "잠실")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("FALLBACK"))
                .andExpect(jsonPath("$.data.warning").value("OpenAI 추천 대신 기본 추천을 제공했습니다."))
                .andExpect(jsonPath("$.data.recommendations[0].name").value("잠실 추천 장소 1"))
                .andExpect(jsonPath("$.data.categories[0].title").value("식당"))
                .andExpect(jsonPath("$.data.routes[0].title").value("기본 코스"));
    }
}

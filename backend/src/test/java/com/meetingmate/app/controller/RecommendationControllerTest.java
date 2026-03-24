package com.meetingmate.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmate.app.dto.recommendation.PlaceRecommendRequest;
import com.meetingmate.app.dto.recommendation.PlaceRecommendResponse;
import com.meetingmate.app.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationController.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ✔ WebMvcTest에서는 직접 생성
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ✔ 핵심: Service Mock
    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    @WithMockUser
    void 장소_추천_API가_정상적으로_응답한다() throws Exception {

        // given
        List<PlaceRecommendResponse> mockResponse = List.of(
                PlaceRecommendResponse.builder()
                        .name("카페 A")
                        .reason("조용하고 대화하기 좋아요")
                        .build(),
                PlaceRecommendResponse.builder()
                        .name("카페 B")
                        .reason("좌석이 넓어요")
                        .build()
        );

        when(recommendationService.recommend(any()))
                .thenReturn(mockResponse);

        PlaceRecommendRequest request = PlaceRecommendRequest.builder()
                .meetingType("친구모임")
                .category("CAFE")
                .preferences(List.of("조용한", "대화하기 좋은"))
                .build();

        // when & then
        mockMvc.perform(
                        post("/api/recommendations")
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("카페 A"));
    }
}

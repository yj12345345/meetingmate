package com.meetingmate.app.exception;

import com.meetingmate.app.repository.UserRepository;
import com.meetingmate.app.security.JwtProvider;
import com.meetingmate.app.dto.recommendation.KeywordRecommendResult;
import com.meetingmate.app.service.RecommendationService;
import com.meetingmate.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A database outage must surface as 503 + Retry-After (never a generic 500), and must
 * not take down endpoints that do not need the database: JWT auth is stateless.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseUnavailableIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserService userService;
    @MockitoBean RecommendationService recommendationService;

    private static CannotCreateTransactionException dbDown() {
        return new CannotCreateTransactionException("Could not open JPA EntityManager",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 3000ms"));
    }

    @Test
    void dbDownDoesNotBreakRecommendationsBecauseJwtAuthIsStateless() throws Exception {
        when(userRepository.findById(anyLong())).thenThrow(dbDown());
        when(recommendationService.recommendByKeyword(any(), any(), any(), any(), any()))
                .thenReturn(KeywordRecommendResult.builder().source("AI").build());

        mvc.perform(get("/api/recommend").param("keyword", "홍대")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isOk());
        // Authentication no longer touches the database at all.
        verifyNoInteractions(userRepository);
    }

    @Test
    void dbDownInsideControllerIs503() throws Exception {
        when(userService.getMyInfo(anyLong())).thenThrow(dbDown());

        mvc.perform(get("/api/user/me").with(user(new com.meetingmate.app.security.UserPrincipal(
                        com.meetingmate.app.domain.user.User.builder().id(1L).build()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }
}

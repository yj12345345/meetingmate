package com.meetingmate.app.exception;

import com.meetingmate.app.repository.UserRepository;
import com.meetingmate.app.security.JwtProvider;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A database outage must surface as 503 + Retry-After, never as a generic 500. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseUnavailableIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserService userService;

    private static CannotCreateTransactionException dbDown() {
        return new CannotCreateTransactionException("Could not open JPA EntityManager",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 3000ms"));
    }

    @Test
    void dbDownDuringJwtAuthenticationIs503() throws Exception {
        when(userRepository.findById(anyLong())).thenThrow(dbDown());

        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
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

package com.meetingmate.app.service;

import com.meetingmate.app.repository.UserRepository;
import com.meetingmate.app.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With stateless JWT auth, a deleted account's unexpired token must still end in 401. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceDeletedUserTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean UserRepository userRepository;

    @Test
    void deletedUsersTokenGets401OnUserEndpoints() throws Exception {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + jwtProvider.createAccessToken(42L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}

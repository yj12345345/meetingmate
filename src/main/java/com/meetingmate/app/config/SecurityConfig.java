package com.meetingmate.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmate.app.exception.ApiErrorResponse;
import com.meetingmate.app.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        ObjectMapper om = new ObjectMapper();

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/test/**").permitAll()
                        .requestMatchers("/api/user/me").authenticated()
                        .anyRequest().authenticated()
                )
                // ✅ 여기 추가: API 요청에서 리다이렉트(302) 대신 JSON 401/403
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            om.writeValue(res.getWriter(), new ApiErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            om.writeValue(res.getWriter(), new ApiErrorResponse("FORBIDDEN", "접근 권한이 없습니다."));
                        })
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .defaultSuccessUrl("/api/groups/me", true)
                );

        return http.build();
    }
}
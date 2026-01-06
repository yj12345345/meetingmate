package com.meetingmate.app.security;

import com.meetingmate.app.domain.user.User;
import com.meetingmate.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Long kakaoId = ((Number) oAuth2User.getAttributes().get("id")).longValue();
        String oauthId = String.valueOf(kakaoId);

        User user = userRepository.findByOauthId(oauthId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .oauthId(oauthId)
                            .email(null)
                            .nickname("카카오유저" + oauthId)
                            .build();
                    return userRepository.save(newUser);
                });

        return new UserPrincipal(user, oAuth2User.getAttributes());
    }
}
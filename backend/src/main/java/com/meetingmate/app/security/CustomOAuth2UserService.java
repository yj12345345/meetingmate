package com.meetingmate.app.security;

import com.meetingmate.app.domain.user.AuthProvider;
import com.meetingmate.app.domain.user.User;
import com.meetingmate.app.repository.UserRepository;
import com.meetingmate.app.security.oauth2.GoogleOAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        User user;
        if ("google".equalsIgnoreCase(registrationId)) {
            user = upsertGoogleUser(oAuth2User);
        } else if ("kakao".equalsIgnoreCase(registrationId)) {
            user = upsertKakaoUser(oAuth2User);
        } else {
            throw new IllegalArgumentException("지원하지 않는 OAuth2 provider: " + registrationId);
        }

        return new UserPrincipal(user, oAuth2User.getAttributes());
    }

    private User upsertGoogleUser(OAuth2User oAuth2User) {
        GoogleOAuth2UserInfo info = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());
        String providerId = info.getProviderId();
        String email = info.getEmail();
        String nickname = info.getNickname();

        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Google providerId(sub)가 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google email 정보가 없습니다.");
        }

        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(AuthProvider.GOOGLE)
                                .providerId(providerId)
                                .email(email)
                                .password(null)
                                .nickname(nickname == null || nickname.isBlank() ? email : nickname)
                                .build()
                ));
    }

    private User upsertKakaoUser(OAuth2User oAuth2User) {
        Long kakaoId = ((Number) oAuth2User.getAttributes().get("id")).longValue();
        String providerId = String.valueOf(kakaoId);
        String email = extractKakaoEmail(oAuth2User);
        String nickname = extractKakaoNickname(oAuth2User, providerId);

        User existingByProvider = userRepository
                .findByProviderIdAndProvider(providerId, AuthProvider.KAKAO)
                .orElse(null);

        if (existingByProvider != null) {
            return existingByProvider;
        }

        if (email != null && !email.isBlank()) {
            User existingByEmail = userRepository.findByEmail(email).orElse(null);
            if (existingByEmail != null) {
                return existingByEmail;
            }
        }

        return userRepository.save(
                User.builder()
                        .provider(AuthProvider.KAKAO)
                        .providerId(providerId)
                        .email(email)
                        .password(null)
                        .nickname(nickname)
                        .build()
        );
    }

    @SuppressWarnings("unchecked")
    private String extractKakaoEmail(OAuth2User oAuth2User) {
        Object kakaoAccount = oAuth2User.getAttributes().get("kakao_account");
        if (!(kakaoAccount instanceof Map<?, ?> accountMap)) {
            return null;
        }
        Object email = accountMap.get("email");
        return email == null ? null : String.valueOf(email);
    }

    @SuppressWarnings("unchecked")
    private String extractKakaoNickname(OAuth2User oAuth2User, String providerId) {
        Object kakaoAccount = oAuth2User.getAttributes().get("kakao_account");
        if (kakaoAccount instanceof Map<?, ?> accountMap) {
            Object profile = accountMap.get("profile");
            if (profile instanceof Map<?, ?> profileMap) {
                Object nickname = profileMap.get("nickname");
                if (nickname != null && !String.valueOf(nickname).isBlank()) {
                    return String.valueOf(nickname);
                }
            }
        }
        return "카카오유저" + providerId;
    }
}

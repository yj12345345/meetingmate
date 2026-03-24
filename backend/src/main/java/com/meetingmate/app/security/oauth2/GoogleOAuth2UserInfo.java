package com.meetingmate.app.security.oauth2;

import java.util.Map;

public class GoogleOAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getProviderId() {
        Object sub = attributes.get("sub");
        return sub == null ? null : String.valueOf(sub);
    }

    public String getEmail() {
        Object email = attributes.get("email");
        return email == null ? null : String.valueOf(email);
    }

    public String getNickname() {
        Object name = attributes.get("name");
        if (name != null && !String.valueOf(name).isBlank()) {
            return String.valueOf(name);
        }
        return getEmail();
    }
}

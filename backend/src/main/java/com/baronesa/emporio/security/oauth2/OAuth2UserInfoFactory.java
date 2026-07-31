package com.baronesa.emporio.security.oauth2;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId,
                                                   Map<String, Object> attributes) {

        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            // case "github" -> new GithubOAuth2UserInfo(attributes);
            default       -> throw new IllegalArgumentException(
                    "Login com provider " + registrationId + " não suportado.");
        };
    }
}

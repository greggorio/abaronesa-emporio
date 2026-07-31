package com.baronesa.website.security;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

public class CustomUserPrincipal implements Principal {
    private final String userId;
    private final String email;
    private final List<String> roles;

    public CustomUserPrincipal(String userId, String email, List<String> roles) {
        this.userId = userId;
        this.email = email;
        this.roles = roles != null ? roles : Collections.emptyList();
    }

    @Override
    public String getName() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "CustomUserPrincipal{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                '}';
    }
}

package com.baronesa.website.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtTokenProvider {

    @Autowired
    private JwtTokenReader tokenReader;

    public String getUserIdFromToken(String token) {
        return tokenReader.getUserIdFromToken(token);
    }

    public String getEmailFromToken(String token) {
        return tokenReader.getEmailFromToken(token);
    }

    public List<String> getRolesFromToken(String token) {
        return tokenReader.getRolesFromToken(token);
    }

    public boolean validateToken(String token) {
        return tokenReader.validateToken(token);
    }
}

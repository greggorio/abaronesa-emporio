package com.baronesa.emporio.security;

import com.baronesa.emporio.entity.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class UserPrincipal implements OAuth2User, UserDetails {

    private final Long               id;
    private final String             email;
    private final String             password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    /* ---------- Factory Methods ---------- */
    public static UserPrincipal create(Usuario usuario) {
        List<SimpleGrantedAuthority> auths = usuario.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();

        return new UserPrincipal(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getSenha(),
                auths,
                new HashMap<>()
        );
    }

    public static UserPrincipal create(Usuario usuario,
                                       Map<String, Object> attributes) {
        return new UserPrincipal(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                        .toList(),
                new HashMap<>(attributes)
        );
    }

    /* ---------- Constructor ---------- */
    private UserPrincipal(Long id,
                          String email,
                          String password,
                          Collection<? extends GrantedAuthority> authorities,
                          Map<String, Object> attributes) {
        this.id          = id;
        this.email       = email;
        this.password    = password;
        this.authorities = authorities;
        this.attributes  = attributes;
    }

    /* ---------- UserDetails ---------- */
    @Override public String getUsername()             { return email; }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }

    /* ---------- OAuth2User ---------- */
    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public String getName() { return String.valueOf(id); }
}

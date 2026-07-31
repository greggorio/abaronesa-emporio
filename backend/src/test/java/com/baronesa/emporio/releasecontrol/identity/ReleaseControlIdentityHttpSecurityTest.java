package com.baronesa.emporio.releasecontrol.identity;

import com.baronesa.emporio.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                ReleaseControlIdentityController.class,
                ReleaseControlIdentityHttpSecurityTest.TestSecurity.class
        },
        properties = {
                "app.release-control.identity.enabled=true",
                "spring.security.oauth2.client.registration.google.client-id=test",
                "spring.security.oauth2.client.registration.google.client-secret=test"
        }
)
@AutoConfigureMockMvc
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
})
class ReleaseControlIdentityHttpSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReleaseControlIdentityService service;

    @Test
    void anonymousIs401AndAdminIs403() throws Exception {
        mvc.perform(post("/api/release-control/identity/token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/release-control/identity/token")
                        .with(user(principal(1L, "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemReceivesExactResponseAndHeadersCannotChangeScope() throws Exception {
        when(service.issue(7L)).thenReturn("opaque-rs256-token");
        mvc.perform(post("/api/release-control/identity/token")
                        .header("X-Scope", "deployment:execute")
                        .header("X-Audience", "other")
                        .with(user(principal(7L, "ROLE_SYSTEM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("opaque-rs256-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.scope").value("release:read release:publish"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void bodyAndQueryAre400BeforeTokenEmission() throws Exception {
        UserPrincipal system = principal(7L, "ROLE_SYSTEM");
        mvc.perform(post("/api/release-control/identity/token?scope=release:read")
                        .with(user(system)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/release-control/identity/token")
                        .content("{}")
                        .with(user(system)))
                .andExpect(status().isBadRequest());
        verify(service, never()).issue(7L);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint(
                                    (request, response, failure) -> response.sendError(401)
                            )
                    )
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(new AntPathRequestMatcher(
                                    "/api/release-control/identity/jwks",
                                    "GET"
                            )).permitAll()
                            .requestMatchers(new AntPathRequestMatcher(
                                    "/api/release-control/identity/token",
                                    "POST"
                            ))
                            .hasRole("SYSTEM")
                            .anyRequest().authenticated()
                    )
                    .build();
        }
    }

    private static UserPrincipal principal(long id, String authority) {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getUsername()).thenReturn("user@example.invalid");
        when(principal.getPassword()).thenReturn("");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getAuthorities()).thenAnswer(
                ignored -> Set.of(new SimpleGrantedAuthority(authority))
        );
        return principal;
    }
}

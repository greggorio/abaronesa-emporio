package com.baronesa.emporio.config;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RootUserInitializerTest {

    private static final String VALID_NAME = "Root";
    private static final String VALID_EMAIL = "root@example.invalid";
    private static final String VALID_PASSWORD = "fixture-only-strong-password";

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private RootUserInitializer initializer(
            boolean enabled, String name, String email, String password) {
        return new RootUserInitializer(repository, encoder, enabled, name, email, password);
    }

    @Test
    void disabledDoesNotCreateOrQueryUser() {
        initializer(false, VALID_NAME, "", "").run(null);
        verify(repository, never()).findByRolesContaining(any());
        verify(repository, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void enabledWithoutEmailFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> initializer(true, VALID_NAME, "", VALID_PASSWORD).run(null));
    }

    @Test
    void enabledWithoutPasswordFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> initializer(true, VALID_NAME, VALID_EMAIL, "").run(null));
    }

    @Test
    void invalidEmailFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> initializer(true, VALID_NAME, "invalid-email", VALID_PASSWORD).run(null));
    }

    @Test
    void shortPasswordFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> initializer(true, VALID_NAME, VALID_EMAIL, "short").run(null));
    }

    @Test
    void existingSystemUserIsNotChanged() {
        Usuario existing = Usuario.builder()
                .email("existing@example.invalid")
                .senha("stored-hash")
                .build();
        when(repository.findByRolesContaining(Usuario.Role.SYSTEM)).thenReturn(List.of(existing));

        initializer(true, VALID_NAME, VALID_EMAIL, VALID_PASSWORD).run(null);

        verify(repository, never()).save(any());
        verify(encoder, never()).encode(any());
        assertEquals("existing@example.invalid", existing.getEmail());
        assertEquals("stored-hash", existing.getSenha());
    }

    @Test
    void validConfigurationCreatesExactlyOneSystemUser() {
        when(repository.findByRolesContaining(Usuario.Role.SYSTEM)).thenReturn(List.of());
        when(encoder.encode(VALID_PASSWORD)).thenReturn("encoded-fixture");

        initializer(true, VALID_NAME, VALID_EMAIL, VALID_PASSWORD).run(null);

        verify(repository).save(any(Usuario.class));
    }

    @Test
    void passwordIsEncodedAndOnlyHashIsPersisted() {
        when(repository.findByRolesContaining(Usuario.Role.SYSTEM)).thenReturn(List.of());
        when(encoder.encode(VALID_PASSWORD)).thenReturn("encoded-fixture");

        initializer(true, VALID_NAME, VALID_EMAIL, VALID_PASSWORD).run(null);

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(encoder).encode(VALID_PASSWORD);
        verify(repository).save(captor.capture());
        assertEquals("encoded-fixture", captor.getValue().getSenha());
        assertTrue(captor.getValue().getRoles().contains(Usuario.Role.SYSTEM));
    }
}

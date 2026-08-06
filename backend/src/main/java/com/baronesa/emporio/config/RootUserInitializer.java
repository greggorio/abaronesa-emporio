package com.baronesa.emporio.config;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RootUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RootUserInitializer.class);
    // O dominio nao exige ponto: hosts locais como root@localhost sao legitimos
    // em desenvolvimento, e a origem do valor ja e confiavel — vem de variavel de
    // ambiente do operador, nao de entrada de usuario.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private static final int MINIMUM_PASSWORD_LENGTH = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String name;
    private final String email;
    private final String password;

    public RootUserInitializer(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.root.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.root.name:Root}") String name,
            @Value("${app.bootstrap.root.email:}") String email,
            @Value("${app.bootstrap.root.password:}") String password) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            log.info("Bootstrap root desabilitado");
            return;
        }

        validateConfiguration();
        if (!usuarioRepository.findByRolesContaining(Usuario.Role.SYSTEM).isEmpty()) {
            log.info("Bootstrap root preservou usuario SYSTEM existente");
            return;
        }

        String passwordHash = passwordEncoder.encode(password);
        Usuario root = Usuario.builder()
                .nome(name.trim())
                .email(email.trim())
                .senha(passwordHash)
                .ativo(true)
                .emailVerificado(true)
                .roles(Set.of(Usuario.Role.SYSTEM))
                .build();

        usuarioRepository.save(root);
        log.info("Bootstrap root criou usuario SYSTEM");
    }

    private void validateConfiguration() {
        if (name == null || name.isBlank()) {
            throw invalidConfiguration("name");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw invalidConfiguration("email");
        }
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw invalidConfiguration("password");
        }
    }

    private IllegalStateException invalidConfiguration(String field) {
        return new IllegalStateException(
                "ROOT_BOOTSTRAP_INVALID_CONFIGURATION: campo obrigatorio invalido: " + field);
    }
}

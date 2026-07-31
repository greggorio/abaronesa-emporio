package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String nome;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(length = 255)
    private String senha;

    @Column(length = 20)
    private String telefone;

    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "email_verificado")
    @Builder.Default
    private Boolean emailVerificado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_usuario")
    private GrupoUsuario grupoUsuario;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(length = 100)
    private String providerId;

    @Column(length = 255)
    private String emailVerificationToken;

    private LocalDateTime emailVerificationExpiresAt;

    @Column(length = 255)
    private String passwordResetToken;

    private LocalDateTime passwordResetExpiresAt;

    @Column(length = 500)
    private String fotoPerfil;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PerfilCliente perfilCliente;

    @Column(name = "cliente_online")
    @Builder.Default
    private Boolean clienteOnline = false;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (roles == null) roles = new HashSet<>();
        if (roles.isEmpty()) roles.add(Role.CLIENTE);
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
        if (roles == null) roles = new HashSet<>();
    }

    public enum AuthProvider {
        LOCAL, GOOGLE
    }

    public enum Role {
        ADMIN,
        FUNCIONARIO,
        CLIENTE,
        SYSTEM,
        KDS,
        WAITER,
        CAIXA
    }

    // Métodos utilitários
    public boolean isAdmin() {
        return roles != null && roles.contains(Role.ADMIN);
    }

    public boolean isFuncionario() {
        return roles != null && roles.contains(Role.FUNCIONARIO);
    }

    public boolean isCliente() {
        return roles != null && roles.contains(Role.CLIENTE);
    }

    public boolean isSystem() {
        return roles != null && roles.contains(Role.SYSTEM);
    }

    public boolean isKds() {
        return roles != null && roles.contains(Role.KDS);
    }

    public boolean isWaiter() {
        return roles != null && roles.contains(Role.WAITER);
    }

    public boolean isCaixa() {
        return roles != null && roles.contains(Role.CAIXA);
    }
}

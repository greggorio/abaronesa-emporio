package com.baronesa.emporio.dto.auth;

import java.util.Set;

import com.baronesa.emporio.entity.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private String nome;
    private String email;
    private String fotoPerfil;
    private Set<Usuario.Role> roles;
}

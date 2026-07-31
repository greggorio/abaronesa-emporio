package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.UsuarioAdminDTO;
import com.baronesa.emporio.dto.UsuarioAdminRequest;
import com.baronesa.emporio.dto.UsuarioAdminUpdateRequest;
import com.baronesa.emporio.entity.GrupoUsuario;
import com.baronesa.emporio.entity.PerfilFuncionario;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.GrupoUsuarioRepository;
import com.baronesa.emporio.repository.PerfilFuncionarioRepository;
import com.baronesa.emporio.repository.UsuarioAdminRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioAdminService {

    private final UsuarioAdminRepository usuarioAdminRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoUsuarioRepository grupoUsuarioRepository;
    private final PerfilFuncionarioRepository perfilFuncionarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void criar(UsuarioAdminRequest request) {
        log.info("Criando usuário admin/funcionário: {}", request.email());

        // Validar email único
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        // Validar roles - não permitir CLIENTE junto com ADMIN/FUNCIONARIO
        if (request.roles().contains(Usuario.Role.CLIENTE)) {
            throw new RuntimeException("Usuários administrativos não podem ter role CLIENTE");
        }

        // Se não foi informada senha, gerar uma temporária
        String senha = request.senha();
        boolean senhaGerada = false;
        if (senha == null || senha.isBlank()) {
            senha = gerarSenhaTemporaria();
            senhaGerada = true;
            log.info("Senha temporária gerada para {}: {}", request.email(), senha);
        }

        // Criar usuário com Set mutável
        Set<Usuario.Role> roles = new HashSet<>(request.roles());

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .telefone(request.telefone())
                .senha(passwordEncoder.encode(senha))
                .ativo(request.ativo() != null ? request.ativo() : true)
                .emailVerificado(true) // Forçar verificação de email
                .roles(roles)
                .build();

        // Associar grupo se informado
        if (request.grupoUsuario() != null) {
            GrupoUsuario grupo = grupoUsuarioRepository.findById(request.grupoUsuario())
                    .orElseThrow(() -> new RuntimeException("Grupo de usuário não encontrado"));
            usuario.setGrupoUsuario(grupo);
        }

        usuarioRepository.save(usuario);

        // Criar PerfilFuncionario com voucherVr se informado
        if (request.voucherVr() != null) {
            PerfilFuncionario perfilFuncionario = PerfilFuncionario.builder()
                    .usuario(usuario)
                    .voucherVr(request.voucherVr())
                    .build();
            perfilFuncionarioRepository.save(perfilFuncionario);
        }

        if (senhaGerada) {
            // TODO: Enviar email com a senha temporária
            log.warn("ATENÇÃO: Enviar senha temporária {} para o email {}", senha, request.email());
        }

        log.info("Usuário admin/funcionário criado com sucesso: {}", usuario.getId());
    }

    @Transactional
    public void editar(Long id, UsuarioAdminUpdateRequest request) {
        log.info("Editando usuário admin/funcionário ID: {}", id);

        Usuario usuario = usuarioAdminRepository.findByIdAdminOrFuncionario(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Validar email único (exceto para o próprio usuário)
        if (!usuario.getEmail().equals(request.email()) && usuarioRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        // Validar roles
        if (request.roles().contains(Usuario.Role.CLIENTE)) {
            throw new RuntimeException("Usuários administrativos não podem ter role CLIENTE");
        }

        // Atualizar dados
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setTelefone(request.telefone());
        usuario.setAtivo(request.ativo());

        // Atualizar senha se informada
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
            log.info("Senha atualizada para usuário ID {}", id);
        }

        // Atualizar roles (criar novo HashSet mutável)
        usuario.setRoles(new HashSet<>(request.roles()));

        // Atualizar grupo
        if (request.grupoUsuario() != null) {
            GrupoUsuario grupo = grupoUsuarioRepository.findById(request.grupoUsuario())
                    .orElseThrow(() -> new RuntimeException("Grupo de usuário não encontrado"));
            usuario.setGrupoUsuario(grupo);
        } else {
            usuario.setGrupoUsuario(null);
        }

        usuarioRepository.save(usuario);

        // Atualizar ou criar PerfilFuncionario com voucherVr
        if (request.voucherVr() != null) {
            PerfilFuncionario perfilFuncionario = perfilFuncionarioRepository.findByUsuarioId(id)
                    .orElse(PerfilFuncionario.builder()
                            .usuario(usuario)
                            .build());
            perfilFuncionario.setVoucherVr(request.voucherVr());
            perfilFuncionarioRepository.save(perfilFuncionario);
        }

        log.info("Usuário admin/funcionário ID {} atualizado com sucesso", id);
    }

    public UsuarioAdminDTO buscarPorId(Long id) {
        Usuario usuario = usuarioAdminRepository.findByIdAdminOrFuncionario(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return entityToDTO(usuario);
    }

    @Transactional
    public void deletar(Long id) {
        log.info("Deletando usuário admin/funcionário ID: {}", id);

        Usuario usuario = usuarioAdminRepository.findByIdAdminOrFuncionario(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Verificar se não é o último ADMIN
        if (usuario.getRoles().contains(Usuario.Role.ADMIN)) {
            long countAdmins = usuarioRepository.findByRolesContaining(Usuario.Role.ADMIN).size();
            if (countAdmins <= 1) {
                throw new RuntimeException("Não é possível deletar o último administrador do sistema");
            }
        }

        usuarioRepository.delete(usuario);
        log.info("Usuário admin/funcionário ID {} deletado com sucesso", id);
    }

    @Transactional
    public void resetarSenha(Long id) {
        log.info("Resetando senha do usuário ID: {}", id);

        Usuario usuario = usuarioAdminRepository.findByIdAdminOrFuncionario(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        String novaSenha = gerarSenhaTemporaria();
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setEmailVerificado(false); // Forçar nova verificação

        usuarioRepository.save(usuario);

        // TODO: Enviar email com a nova senha
        log.warn("ATENÇÃO: Enviar nova senha {} para o email {}", novaSenha, usuario.getEmail());
    }

    private String gerarSenhaTemporaria() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UsuarioAdminDTO entityToDTO(Usuario usuario) {
        String rolesDisplay = usuario.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));

        // Obter o valor de voucherVr do PerfilFuncionario
        BigDecimal voucherVr = null;
        if (usuario.getId() != null) {
            Optional<PerfilFuncionario> perfilFuncionario = perfilFuncionarioRepository.findByUsuarioId(usuario.getId());
            if (perfilFuncionario.isPresent()) {
                voucherVr = perfilFuncionario.get().getVoucherVr();
            }
        }

        return new UsuarioAdminDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getAtivo(),
                usuario.getEmailVerificado(),
                usuario.getRoles(),
                rolesDisplay,
                usuario.getGrupoUsuario() != null ? usuario.getGrupoUsuario().getId() : null,
                usuario.getGrupoUsuario() != null ? usuario.getGrupoUsuario().getDescricao() : null,
                usuario.getCriadoEm(),
                usuario.getUltimoLogin(),
                voucherVr
        );
    }
}
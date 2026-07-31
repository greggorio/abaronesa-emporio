package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.GrupoUsuarioDTO;
import com.baronesa.emporio.dto.GrupoUsuarioOptionDTO;
import com.baronesa.emporio.dto.GrupoUsuarioRequest;
import com.baronesa.emporio.entity.GrupoUsuario;
import com.baronesa.emporio.repository.GrupoUsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrupoUsuarioService {

    private final GrupoUsuarioRepository grupoUsuarioRepository;

    public List<GrupoUsuarioDTO> listarTodos() {
        return grupoUsuarioRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<GrupoUsuarioOptionDTO> listarOptions() {
        log.info("Listando options de grupos de usuários");

        List<GrupoUsuario> grupos = grupoUsuarioRepository.findAll();
        log.debug("Total de grupos encontrados: {}", grupos.size());

        List<GrupoUsuarioOptionDTO> options = grupos.stream()
                .filter(g -> {
                    boolean isAtivo = g.getAtivo() == null || g.getAtivo();
                    log.debug("Grupo {}: ativo={}", g.getDescricao(), isAtivo);
                    return isAtivo;
                })
                .map(g -> new GrupoUsuarioOptionDTO(g.getId(), g.getDescricao()))
                .toList();

        log.info("Retornando {} options de grupos", options.size());
        return options;
    }

    public GrupoUsuarioDTO buscarPorId(Long id) {
        GrupoUsuario grupo = grupoUsuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grupo de usuário não encontrado"));
        return toDTO(grupo);
    }

    @Transactional
    public void criar(GrupoUsuarioRequest request) {
        GrupoUsuario grupo = GrupoUsuario.builder()
                .descricao(request.descricao())
                .ativo(request.ativo() != null ? request.ativo() : true)
                .build();
        grupoUsuarioRepository.save(grupo);
    }

    @Transactional
    public void editar(Long id, GrupoUsuarioRequest request) {
        GrupoUsuario grupo = grupoUsuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grupo de usuário não encontrado"));

        grupo.setDescricao(request.descricao());
        grupo.setAtivo(request.ativo());

        grupoUsuarioRepository.save(grupo);
    }

    @Transactional
    public void deletar(Long id) {
        grupoUsuarioRepository.deleteById(id);
    }

    private GrupoUsuarioDTO toDTO(GrupoUsuario grupo) {
        return new GrupoUsuarioDTO(
                grupo.getId(),
                grupo.getDescricao(),
                grupo.getAtivo()
        );
    }
}

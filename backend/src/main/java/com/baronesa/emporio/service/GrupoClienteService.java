package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.GrupoClienteDTO;
import com.baronesa.emporio.dto.GrupoClienteOptionDTO;
import com.baronesa.emporio.dto.GrupoClienteRequest;
import com.baronesa.emporio.entity.GrupoCliente;
import com.baronesa.emporio.repository.GrupoClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupoClienteService {

    private final GrupoClienteRepository grupoClienteRepository;

    @Transactional
    public void criar(GrupoClienteRequest request) {
        // Validar descrição única
        if (grupoClienteRepository.existsByDescricaoIgnoreCase(request.descricao())) {
            throw new RuntimeException("Já existe um grupo com esta descrição");
        }

        GrupoCliente grupoCliente = GrupoCliente.builder()
                .descricao(request.descricao())
                .build();

        grupoClienteRepository.save(grupoCliente);
    }

    @Transactional
    public void editar(Long id, GrupoClienteRequest request) {
        GrupoCliente grupoCliente = grupoClienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grupo de cliente não encontrado"));

        // Validar descrição única (exceto para o próprio grupo)
        grupoClienteRepository.findByDescricaoIgnoreCase(request.descricao())
                .ifPresent(g -> {
                    if (!g.getId().equals(id)) {
                        throw new RuntimeException("Já existe um grupo com esta descrição");
                    }
                });

        grupoCliente.setDescricao(request.descricao());
        grupoClienteRepository.save(grupoCliente);
    }

    public GrupoClienteDTO buscarPorId(Long id) {
        GrupoCliente grupoCliente = grupoClienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grupo de cliente não encontrado"));

        return new GrupoClienteDTO(
                grupoCliente.getId(),
                grupoCliente.getDescricao()
        );
    }

    public List<GrupoClienteDTO> listarTodos() {
        return grupoClienteRepository.findAll().stream()
                .map(g -> new GrupoClienteDTO(g.getId(), g.getDescricao()))
                .toList();
    }

    public List<GrupoClienteOptionDTO> listarOptions() {
        return grupoClienteRepository.findAll().stream()
                .map(g -> new GrupoClienteOptionDTO(g.getId(), g.getDescricao()))
                .toList();
    }

    @Transactional
    public void deletar(Long id) {
        // Aqui você pode adicionar validação para verificar se o grupo está sendo usado
        // por algum cliente antes de permitir a exclusão
        grupoClienteRepository.deleteById(id);
    }
}
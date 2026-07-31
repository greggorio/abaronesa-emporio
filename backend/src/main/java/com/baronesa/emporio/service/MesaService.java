package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.MesaDTO;
import com.baronesa.emporio.dto.MesaOptionDTO;
import com.baronesa.emporio.dto.MesaRequest;
import com.baronesa.emporio.entity.Mesa;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.MesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MesaService {

    private final MesaRepository mesaRepository;

    public void criar(MesaRequest request) {
        Mesa mesa = Mesa.builder()
                .slug(request.slug())
                .rotulo(request.rotulo())
                .referencia(request.referencia())
                .ativo(request.ativo() != null ? request.ativo() : true)
                .build();

        mesaRepository.save(mesa);
    }

    public void editar(Long id, MesaRequest request) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada"));

        mesa.setSlug(request.slug());
        mesa.setRotulo(request.rotulo());
        mesa.setReferencia(request.referencia());
        if (request.ativo() != null) {
            mesa.setAtivo(request.ativo());
        }

        mesaRepository.save(mesa);
    }

    public void deletar(Long id) {
        mesaRepository.deleteById(id);
    }

    public MesaDTO buscarPorId(Long id) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada"));

        return new MesaDTO(
                mesa.getId(),
                mesa.getSlug(),
                mesa.getRotulo(),
                mesa.getReferencia(),
                mesa.getAtivo(),
                mesa.getCriadoEm(),
                mesa.getAtualizadoEm()
        );
    }

    public List<MesaOptionDTO> listarOptions() {
        return mesaRepository.findAll().stream()
                .map(m -> new MesaOptionDTO(m.getId(), m.getRotulo(), m.getReferencia()))
                .toList();
    }

    public void atualizarReferencia(String slug, String referencia) {
        Mesa mesa = mesaRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Mesa não encontrada: " + slug));

        String valor = referencia != null ? referencia.trim() : null;
        if (valor != null && valor.isEmpty()) {
            valor = null;
        }
        if (valor != null && valor.length() > 200) {
            throw new IllegalArgumentException("A referência deve ter no máximo 200 caracteres.");
        }

        mesa.setReferencia(valor);
        mesaRepository.save(mesa);
    }
}

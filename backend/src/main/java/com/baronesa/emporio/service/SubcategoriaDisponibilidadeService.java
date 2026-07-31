package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SubcategoriaDisponibilidadeDTO;
import com.baronesa.emporio.dto.SubcategoriaDisponibilidadeRequest;
import com.baronesa.emporio.entity.Subcategoria;
import com.baronesa.emporio.entity.SubcategoriaDisponibilidade;
import com.baronesa.emporio.repository.SubcategoriaDisponibilidadeRepository;
import com.baronesa.emporio.repository.SubcategoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SubcategoriaDisponibilidadeService {

    private final SubcategoriaDisponibilidadeRepository disponibilidadeRepository;
    private final SubcategoriaRepository subcategoriaRepository;

    public SubcategoriaDisponibilidadeDTO criar(SubcategoriaDisponibilidadeRequest request) {
        validarRequest(request);

        Subcategoria subcategoria = subcategoriaRepository.findById(request.getSubcategoriaId())
                .orElseThrow(() -> new EntityNotFoundException("Subcategoria não encontrada: " + request.getSubcategoriaId()));

        SubcategoriaDisponibilidade entidade = SubcategoriaDisponibilidade.builder()
                .subcategoria(subcategoria)
                .diaSemana(request.getDiaSemana())
                .horarioInicio(request.getHorarioInicio())
                .horarioFim(request.getHorarioFim())
                .ativo(request.getAtivo() != null ? request.getAtivo() : true)
                .build();

        entidade = disponibilidadeRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    public SubcategoriaDisponibilidadeDTO atualizar(Long id, SubcategoriaDisponibilidadeRequest request) {
        SubcategoriaDisponibilidade entidade = disponibilidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id));

        if (request.getSubcategoriaId() != null && !request.getSubcategoriaId().equals(entidade.getSubcategoria().getId())) {
             Subcategoria subcategoria = subcategoriaRepository.findById(request.getSubcategoriaId())
                .orElseThrow(() -> new EntityNotFoundException("Subcategoria não encontrada: " + request.getSubcategoriaId()));
             entidade.setSubcategoria(subcategoria);
        }

        if (request.getDiaSemana() != null) entidade.setDiaSemana(request.getDiaSemana());
        if (request.getHorarioInicio() != null) entidade.setHorarioInicio(request.getHorarioInicio());
        if (request.getHorarioFim() != null) entidade.setHorarioFim(request.getHorarioFim());
        if (request.getAtivo() != null) entidade.setAtivo(request.getAtivo());

        if (entidade.getHorarioFim().isBefore(entidade.getHorarioInicio()) && !entidade.getHorarioFim().equals(entidade.getHorarioInicio())) {
             throw new IllegalArgumentException("Horário final deve ser posterior ao horário inicial.");
        }

        entidade = disponibilidadeRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    @Transactional(readOnly = true)
    public List<SubcategoriaDisponibilidadeDTO> listarPorSubcategoria(Long subcategoriaId) {
        if (!subcategoriaRepository.existsById(subcategoriaId)) {
             throw new EntityNotFoundException("Subcategoria não encontrada: " + subcategoriaId);
        }
        return disponibilidadeRepository.findBySubcategoriaId(subcategoriaId).stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubcategoriaDisponibilidadeDTO buscarPorId(Long id) {
        SubcategoriaDisponibilidade entidade = disponibilidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id));
        return converterParaDTO(entidade);
    }

    public void deletar(Long id) {
        if (!disponibilidadeRepository.existsById(id)) {
            throw new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id);
        }
        disponibilidadeRepository.deleteById(id);
    }

    private void validarRequest(SubcategoriaDisponibilidadeRequest request) {
        if (request.getSubcategoriaId() == null) {
            throw new IllegalArgumentException("ID da subcategoria é obrigatório");
        }
        if (request.getDiaSemana() == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        if (request.getHorarioInicio() == null || request.getHorarioFim() == null) {
            throw new IllegalArgumentException("Horários de início e fim são obrigatórios");
        }
        if (request.getHorarioFim().isBefore(request.getHorarioInicio())) {
            throw new IllegalArgumentException("Horário final deve ser posterior ao horário inicial.");
        }
    }

    private SubcategoriaDisponibilidadeDTO converterParaDTO(SubcategoriaDisponibilidade entidade) {
        return SubcategoriaDisponibilidadeDTO.builder()
                .id(entidade.getId())
                .subcategoriaId(entidade.getSubcategoria().getId())
                .subcategoriaNome(entidade.getSubcategoria().getNome())
                .diaSemana(entidade.getDiaSemana())
                .horarioInicio(entidade.getHorarioInicio())
                .horarioFim(entidade.getHorarioFim())
                .ativo(entidade.getAtivo())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .build();
    }
}

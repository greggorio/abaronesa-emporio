package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ProdutoDisponibilidadeDTO;
import com.baronesa.emporio.dto.ProdutoDisponibilidadeRequest;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoDisponibilidade;
import com.baronesa.emporio.repository.ProdutoDisponibilidadeRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProdutoDisponibilidadeService {

    private final ProdutoDisponibilidadeRepository disponibilidadeRepository;
    private final ProdutoRepository produtoRepository;

    public ProdutoDisponibilidadeDTO criar(ProdutoDisponibilidadeRequest request) {
        validarRequest(request);

        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + request.getProdutoId()));

        ProdutoDisponibilidade entidade = ProdutoDisponibilidade.builder()
                .produto(produto)
                .diaSemana(request.getDiaSemana())
                .horarioInicio(request.getHorarioInicio())
                .horarioFim(request.getHorarioFim())
                .ativo(request.getAtivo() != null ? request.getAtivo() : true)
                .build();

        entidade = disponibilidadeRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    public ProdutoDisponibilidadeDTO atualizar(Long id, ProdutoDisponibilidadeRequest request) {
        ProdutoDisponibilidade entidade = disponibilidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id));

        // Se o produtoId for informado e diferente, validar (embora raramente se mude o produto de uma regra)
        if (request.getProdutoId() != null && !request.getProdutoId().equals(entidade.getProduto().getId())) {
             Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + request.getProdutoId()));
             entidade.setProduto(produto);
        }

        if (request.getDiaSemana() != null) entidade.setDiaSemana(request.getDiaSemana());
        if (request.getHorarioInicio() != null) entidade.setHorarioInicio(request.getHorarioInicio());
        if (request.getHorarioFim() != null) entidade.setHorarioFim(request.getHorarioFim());
        if (request.getAtivo() != null) entidade.setAtivo(request.getAtivo());
        
        // Revalidar consistência de horários após setters
        if (entidade.getHorarioFim().isBefore(entidade.getHorarioInicio()) && !entidade.getHorarioFim().equals(entidade.getHorarioInicio())) {
             // Permitir igual? Geralmente não faz sentido, mas vamos assumir que não pode ser menor.
             // Se for transição de dia (ex: 23:00 as 02:00) isso requer lógica complexa.
             // Por simplicidade, assumiremos que o horário deve estar no mesmo dia (00:00 as 23:59).
             throw new IllegalArgumentException("Horário final deve ser posterior ao horário inicial.");
        }

        entidade = disponibilidadeRepository.save(entidade);
        return converterParaDTO(entidade);
    }

    @Transactional(readOnly = true)
    public List<ProdutoDisponibilidadeDTO> listarPorProduto(Long produtoId) {
        if (!produtoRepository.existsById(produtoId)) {
             throw new EntityNotFoundException("Produto não encontrado: " + produtoId);
        }
        return disponibilidadeRepository.findByProdutoId(produtoId).stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProdutoDisponibilidadeDTO buscarPorId(Long id) {
        ProdutoDisponibilidade entidade = disponibilidadeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id));
        return converterParaDTO(entidade);
    }

    public void deletar(Long id) {
        if (!disponibilidadeRepository.existsById(id)) {
            throw new EntityNotFoundException("Regra de disponibilidade não encontrada: " + id);
        }
        disponibilidadeRepository.deleteById(id);
    }

    private void validarRequest(ProdutoDisponibilidadeRequest request) {
        if (request.getProdutoId() == null) {
            throw new IllegalArgumentException("ID do produto é obrigatório");
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

    private ProdutoDisponibilidadeDTO converterParaDTO(ProdutoDisponibilidade entidade) {
        return ProdutoDisponibilidadeDTO.builder()
                .id(entidade.getId())
                .produtoId(entidade.getProduto().getId())
                .produtoNome(entidade.getProduto().getNome())
                .diaSemana(entidade.getDiaSemana())
                .horarioInicio(entidade.getHorarioInicio())
                .horarioFim(entidade.getHorarioFim())
                .ativo(entidade.getAtivo())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .build();
    }
}

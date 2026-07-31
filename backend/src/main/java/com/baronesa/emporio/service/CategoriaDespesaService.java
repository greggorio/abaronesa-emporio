package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.CategoriaDespesaDTO;
import com.baronesa.emporio.dto.CategoriaDespesaOptionDTO;
import com.baronesa.emporio.dto.CategoriaDespesaRequest;
import com.baronesa.emporio.entity.CategoriaDespesa;
import com.baronesa.emporio.repository.CategoriaDespesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaDespesaService {

    private final CategoriaDespesaRepository categoriaDespesaRepository;

    public void criar(CategoriaDespesaRequest request) {
        CategoriaDespesa categoriaDespesa = CategoriaDespesa.builder()
                .nome(request.nome())
                .build();

        categoriaDespesaRepository.save(categoriaDespesa);
    }

    public void editar(Long id, CategoriaDespesaRequest request) {
        CategoriaDespesa categoriaDespesa = categoriaDespesaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria de Despesa não encontrada"));

        categoriaDespesa.setNome(request.nome());

        categoriaDespesaRepository.save(categoriaDespesa);
    }

    public void deletar(Long id) {
        categoriaDespesaRepository.deleteById(id);
    }

    public CategoriaDespesaDTO buscarPorId(Long id) {
        CategoriaDespesa categoriaDespesa = categoriaDespesaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria de Despesa não encontrada"));

        return new CategoriaDespesaDTO(
                categoriaDespesa.getId(),
                categoriaDespesa.getNome()
        );
    }

    public List<CategoriaDespesaOptionDTO> listarOptions() {
        return categoriaDespesaRepository.findAll().stream()
                .map(cd -> new CategoriaDespesaOptionDTO(cd.getId(), cd.getNome()))
                .toList();
    }
}

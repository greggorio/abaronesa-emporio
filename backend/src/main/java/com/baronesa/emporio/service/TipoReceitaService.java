package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.TipoReceitaDTO;
import com.baronesa.emporio.dto.TipoReceitaOptionDTO;
import com.baronesa.emporio.dto.TipoReceitaRequest;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TipoReceitaService {

    private final TipoReceitaRepository tipoReceitaRepository;

    public void criar(TipoReceitaRequest request) {
        TipoReceita tipoReceita = TipoReceita.builder()
                .nome(request.nome())
                .build();

        tipoReceitaRepository.save(tipoReceita);
    }

    public void editar(Long id, TipoReceitaRequest request) {
        TipoReceita tipoReceita = tipoReceitaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tipo de Receita não encontrado"));

        tipoReceita.setNome(request.nome());

        tipoReceitaRepository.save(tipoReceita);
    }

    public void deletar(Long id) {
        tipoReceitaRepository.deleteById(id);
    }

    public TipoReceitaDTO buscarPorId(Long id) {
        TipoReceita tipoReceita = tipoReceitaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tipo de Receita não encontrado"));

        return new TipoReceitaDTO(
                tipoReceita.getId(),
                tipoReceita.getNome()
        );
    }

    public List<TipoReceitaOptionDTO> listarOptions() {
        return tipoReceitaRepository.findAll().stream()
                .map(tr -> new TipoReceitaOptionDTO(tr.getId(), tr.getNome()))
                .toList();
    }
}

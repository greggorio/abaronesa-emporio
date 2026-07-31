package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.RecompensaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecompensaService {

    private final RecompensaRepository recompensaRepository;

    @Transactional(readOnly = true)
    public List<Recompensa> getAll() {
        return recompensaRepository.findAll();
    }

    @Transactional
    public Recompensa create(Recompensa recompensa) {
        RecompensaValidator.validate(recompensa);
        return recompensaRepository.save(recompensa);
    }

    @Transactional
    public Recompensa update(Long id, Recompensa payload) {
        Recompensa existente = recompensaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Recompensa não encontrada: " + id));

        existente.setNome(payload.getNome());
        existente.setDescricao(payload.getDescricao());
        existente.setPontosNecessarios(payload.getPontosNecessarios());
        existente.setTipo(payload.getTipo());
        existente.setProdutoId(payload.getProdutoId());
        existente.setEstoque(payload.getEstoque());
        existente.setAtivo(payload.getAtivo());
        existente.setValidadeInicio(payload.getValidadeInicio());
        existente.setValidadeFim(payload.getValidadeFim());
        existente.setImageUrl(payload.getImageUrl());
        existente.setDescontoPercentual(payload.getDescontoPercentual());
        existente.setDescontoValor(payload.getDescontoValor());
        existente.setDescontoValorMaximo(payload.getDescontoValorMaximo());

        RecompensaValidator.validate(existente);
        return recompensaRepository.save(existente);
    }
}

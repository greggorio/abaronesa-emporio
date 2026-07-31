package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.MovimentoPontos;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.MovimentoPontosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GamificacaoConsultaService {

    private final MovimentoPontosRepository movimentoPontosRepository;

    public Map<String, Object> buscarMinhaGamificacao(Usuario usuario) {
        Optional<MovimentoPontos> ultimo = movimentoPontosRepository.findFirstByClienteOrderByDataHoraDesc(usuario);
        int saldo = ultimo
                .map(MovimentoPontos::getSaldoApos)
                .filter(s -> s != null)
                .orElse(0);

        List<MovimentoPontos> movimentos = movimentoPontosRepository.findTop50ByClienteOrderByDataHoraDesc(usuario);
        List<Map<String, Object>> extrato = movimentos.stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("dataHora", m.getDataHora());
                    map.put("tipo", m.getTipo() != null ? m.getTipo().name() : null);
                    map.put("origem", m.getOrigem() != null ? m.getOrigem().name() : null);
                    map.put("pontos", m.getPontos());
                    map.put("saldoApos", m.getSaldoApos());
                    map.put("referenciaTipo", m.getReferenciaTipo());
                    map.put("referenciaId", m.getReferenciaId());
                    map.put("observacao", m.getObservacao());
                    return map;
                })
                .collect(Collectors.toList());

        return Map.of(
                "saldo", saldo,
                "extrato", extrato
        );
    }
}

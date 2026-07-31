package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.GamificacaoEventoTipo;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.MovimentoPontos;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.OrigemMovimentoPontos;
import com.baronesa.emporio.enums.TipoMovimentoPontos;
import com.baronesa.emporio.repository.MovimentoPontosRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GamificacaoService {

    private final MovimentoPontosRepository movimentoPontosRepository;
    private final ConfigManager configManager;
    private final UsuarioService usuarioService;

    public void registrarConsumoItemAceito(ItemPedido item, Usuario cliente) {
        if (item == null || cliente == null || item.getId() == null) {
            return;
        }

        BigDecimal precoUnitario = item.getPrecoUnitario();
        Integer quantidade = item.getQuantidade();
        if (precoUnitario == null || quantidade == null || quantidade <= 0) {
            log.warn("Gamificação ignorada para item {}: preço ou quantidade inválidos", item.getId());
            return;
        }

        BigDecimal valorItem = precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        GamificacaoEvento evento = GamificacaoEvento.builder()
                .tipo(GamificacaoEventoTipo.ITEM_PEDIDO_ACEITO)
                .referenciaId(item.getId())
                .cliente(cliente)
                .valor(valorItem)
                .quantidade(quantidade)
                .pedidoId(item.getPedido() != null ? item.getPedido().getId() : null)
                .build();

        processarEvento(evento);
    }

    public void processarEvento(GamificacaoEvento evento) {
        if (evento == null || evento.getCliente() == null || evento.getReferenciaId() == null) {
            return;
        }

        String referenciaTipo = evento.getTipo() != null ? evento.getTipo().name() : null;
        Long referenciaId = evento.getReferenciaId();

        if (referenciaTipo == null) {
            log.warn("Evento de gamificação sem tipo definido para referência {}", referenciaId);
            return;
        }

        if (movimentoPontosRepository.existsByReferenciaTipoAndReferenciaId(referenciaTipo, referenciaId)) {
            log.debug("Gamificação ignorada: evento duplicado {}#{} já registrado", referenciaTipo, referenciaId);
            return;
        }

        BigDecimal valor = evento.getValor();
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Evento de gamificação ignorado (valor inválido) {}", referenciaId);
            return;
        }

        if (!configManager.isGamificacaoAtiva()) {
            log.debug("Gamificação está desativada via configurações. Ignorando evento {}", referenciaId);
            return;
        }

        BigDecimal valorPara1Ponto = configManager.getGamificacaoValorPara1Ponto();
        if (valorPara1Ponto == null || valorPara1Ponto.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Configuração de gamificação inválida (valor_para_1_ponto) detectada nas configs. Ignorando evento {}", referenciaId);
            return;
        }

        RoundingMode arredondamento = configManager.getGamificacaoArredondamento();

        BigDecimal pontosBrutos = valor.divide(valorPara1Ponto, 10, RoundingMode.HALF_UP);
        BigDecimal pontosAjustados = pontosBrutos.setScale(0, arredondamento);
        int pontos = pontosAjustados.intValue();
        if (pontos <= 0) {
            log.info("Evento de gamificação {} ignorado: pontos calculados = {}", referenciaId, pontos);
            return;
        }

        Usuario cliente = evento.getCliente();
        Optional<MovimentoPontos> ultimoMovimento = movimentoPontosRepository.findFirstByClienteOrderByDataHoraDesc(cliente);
        int saldoAnterior = ultimoMovimento
                .map(MovimentoPontos::getSaldoApos)
                .filter(Objects::nonNull)
                .orElse(0);

        MovimentoPontos movimento = new MovimentoPontos();
        movimento.setCliente(cliente);
        movimento.setTipo(TipoMovimentoPontos.GANHO);
        movimento.setOrigem(OrigemMovimentoPontos.VENDA);
        movimento.setReferenciaTipo(referenciaTipo);
        movimento.setReferenciaId(referenciaId);
        movimento.setPontos(pontos);
        movimento.setSaldoApos(saldoAnterior + pontos);
        movimento.setDataHora(LocalDateTime.now());
        Long pedidoId = evento.getPedidoId();
        movimento.setObservacao("Evento " + evento.getTipo() + " (pedido #" + (pedidoId != null ? pedidoId : "") + ")");

        movimentoPontosRepository.save(movimento);

        log.info("Movimento de pontos criado para evento {}#{} (pedido {}", referenciaTipo, referenciaId, pedidoId, cliente.getId(), pontos, saldoAnterior);
    }
    
    public Integer getSaldoCliente(Long clienteId) {
        Optional<Usuario> clienteOpt = usuarioService.findById(clienteId);
        if (clienteOpt.isEmpty()) {
            throw new RuntimeException("Cliente não encontrado: " + clienteId);
        }
        
        return movimentoPontosRepository.findFirstByClienteOrderByDataHoraDesc(clienteOpt.get())
            .map(MovimentoPontos::getSaldoApos)
            .orElse(0);
    }

    public Map<Long, Integer> getSaldosClientes(List<Long> clienteIds) {
        Map<Long, Integer> saldos = new HashMap<>();
        if (clienteIds == null || clienteIds.isEmpty()) {
            return saldos;
        }

        // Busca último saldo por cliente usando DISTINCT ON
        movimentoPontosRepository.findLatestSaldoByClientes(clienteIds)
            .forEach(row -> {
                Long clienteId = ((Number) row[0]).longValue();
                Integer saldo = row[1] != null ? ((Number) row[1]).intValue() : 0;
                saldos.put(clienteId, saldo);
            });

        // Preencher com 0 para clientes sem movimentos
        clienteIds.forEach(id -> saldos.putIfAbsent(id, 0));
        return saldos;
    }
}

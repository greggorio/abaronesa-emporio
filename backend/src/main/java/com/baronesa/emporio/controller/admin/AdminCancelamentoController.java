package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.CancelamentoItemDTO;
import com.baronesa.emporio.enums.MotivoCancelamentoItem;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/cancelamentos")
@RequiredArgsConstructor
public class AdminCancelamentoController {

    private final ItemPedidoRepository itemPedidoRepository;

    @GetMapping("/hoje")
    public ResponseEntity<Map<String, Object>> kpiHoje() {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = hoje.atStartOfDay();
        LocalDateTime fim = hoje.plusDays(1).atStartOfDay();
        var cancelados = itemPedidoRepository.findCanceledBetween(inicio, fim);
        long totalItens = cancelados.stream().mapToLong(ip -> ip.getQuantidade() != null ? ip.getQuantidade() : 0).sum();
        BigDecimal valorTotal = cancelados.stream()
                .map(ip -> ip.getPrecoUnitario().multiply(BigDecimal.valueOf(ip.getQuantidade() != null ? ip.getQuantidade() : 0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(Map.of(
                "totalItens", totalItens,
                "valorTotal", valorTotal
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        LocalDateTime start = inicio.atStartOfDay();
        LocalDateTime end = fim.plusDays(1).atStartOfDay();

        List<CancelamentoItemDTO> itens = itemPedidoRepository.findCanceledBetween(start, end)
                .stream()
                .map(ip -> CancelamentoItemDTO.builder()
                        .itemPedidoId(ip.getId())
                        .pedidoId(ip.getPedido() != null ? ip.getPedido().getId() : null)
                        .produtoNome(ip.getProduto() != null ? ip.getProduto().getNome() : null)
                        .quantidade(ip.getQuantidade())
                        .precoUnitario(ip.getPrecoUnitario())
                        .valorTotal(ip.getPrecoUnitario().multiply(BigDecimal.valueOf(ip.getQuantidade() != null ? ip.getQuantidade() : 0)))
                        .status(ip.getStatus())
                        .mesaSlug(ip.getPedido() != null && ip.getPedido().getSessaoMesa() != null && ip.getPedido().getSessaoMesa().getMesa() != null
                                ? ip.getPedido().getSessaoMesa().getMesa().getSlug()
                                : null)
                        .mesaRotulo(ip.getPedido() != null && ip.getPedido().getSessaoMesa() != null && ip.getPedido().getSessaoMesa().getMesa() != null
                                ? ip.getPedido().getSessaoMesa().getMesa().getRotulo()
                                : null)
                        .criadoEm(ip.getPedido() != null ? ip.getPedido().getCriadoEm() : null)
                        .motivoCodigo(ip.getMotivoCancelamentoCodigo())
                        .motivoDetalhe(ip.getMotivoCancelamento())
                        .build())
                .toList();

        long totalItens = itens.stream().mapToLong(i -> i.getQuantidade() != null ? i.getQuantidade() : 0).sum();
        BigDecimal valorTotal = itens.stream()
                .map(i -> i.getValorTotal() != null ? i.getValorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
                "itens", itens,
                "totalItens", totalItens,
                "valorTotal", valorTotal,
                "motivos", MotivoCancelamentoItem.values()
        ));
    }
}

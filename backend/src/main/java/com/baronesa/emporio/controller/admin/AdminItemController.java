package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.CancelamentoItemDTO;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.enums.MotivoCancelamentoItem;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/itens")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM','WAITER','CAIXA')")
public class AdminItemController {

    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoService pedidoService;
    private final PagamentoRepository pagamentoRepository;

    @GetMapping("/sessoes/{sessaoMesaId}")
    public ResponseEntity<Map<String, Object>> listarItensPorSessao(@PathVariable Long sessaoMesaId) {
        List<ItemPedido> itens = itemPedidoRepository.findByPedido_SessaoMesaId(sessaoMesaId);
        List<CancelamentoItemDTO> dto = itens.stream().map(this::toDto).toList();
        return ResponseEntity.ok(Map.of("itens", dto));
    }

    @PostMapping("/{itemPedidoId}/cancelar")
    public ResponseEntity<?> cancelarItem(@PathVariable Long itemPedidoId,
                                          @RequestBody Map<String, String> body) {
        ItemPedido item = itemPedidoRepository.findById(itemPedidoId)
                .orElseThrow(() -> new NotFoundException("Item do pedido não encontrado"));

        // Bloquear se já houve pagamento na sessão
        boolean hasPayment = pagamentoRepository.existsBySessaoMesaAndStatus(item.getPedido().getSessaoMesa(), com.baronesa.emporio.enums.StatusPagamento.PAID);
        if (hasPayment) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", Map.of(
                            "code", "pagamento_existente",
                            "message", "Não é possível cancelar itens após pagamento registrado."
                    )
            ));
        }

        String motivoCodigo = body != null ? body.get("motivoCodigo") : null;
        String motivoDetalhe = body != null ? body.get("motivoDetalhe") : null;

        ItemPedido atualizado = pedidoService.atualizarStatusItem(item.getId(), StatusItem.CANCELED, motivoCodigo, motivoDetalhe);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "item", toDto(atualizado)
        ));
    }

    private CancelamentoItemDTO toDto(ItemPedido ip) {
        return CancelamentoItemDTO.builder()
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
                .build();
    }
}

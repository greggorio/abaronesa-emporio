package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.MovimentoEstoqueAdminDTO;
import com.baronesa.emporio.service.MovimentoEstoqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/itens")
@RequiredArgsConstructor
public class AdminItemMovimentoController {

    private final MovimentoEstoqueService movimentoEstoqueService;

    @GetMapping("/{itemPedidoId}/movimentos-estoque")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
    public ResponseEntity<Map<String, Object>> listarMovimentosEstoque(@PathVariable Long itemPedidoId) {
        List<MovimentoEstoqueAdminDTO> movimentos = movimentoEstoqueService.listarMovimentosPorItemPedidoId(itemPedidoId);
        
        return ResponseEntity.ok(Map.of("movimentos", movimentos));
    }
}
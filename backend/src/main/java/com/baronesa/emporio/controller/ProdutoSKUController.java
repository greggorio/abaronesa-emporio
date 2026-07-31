package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dto.SKUOptionDTO;
import com.baronesa.emporio.dto.AtualizarEmbalagemSKURequest;
import com.baronesa.emporio.service.ProdutoSKUService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skus")
@RequiredArgsConstructor
public class ProdutoSKUController {

    private final ProdutoSKUService skuService;
    private final MessageResolver messageResolver;

    /**
     * Lista todos os SKUs ativos como options
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<SKUOptionDTO> options = skuService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    /**
     * Busca SKUs para autocomplete com suporte a paginação
     * Usado pelo q-select com use-input
     */
    @GetMapping("/search-options")
    public ResponseEntity<Map<String, Object>> buscarOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SKUOptionDTO> resultado = skuService.buscarOptionsPaginado(q, produtoId, PageRequest.of(page, size));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", resultado.getContent(),
                "hasMore", resultado.hasNext(),
                "totalElements", resultado.getTotalElements()
        ));
    }

    /**
     * Busca SKU específico por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> buscarPorId(@PathVariable Long id) {
        try {
            var sku = skuService.buscarPorId(id);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("id", sku.getId());
            data.put("sku", sku.getSku());
            data.put("codigoBarras", sku.getCodigoBarras());
            data.put("produto", sku.getProduto() != null ? sku.getProduto().getNome() : null);
            data.put("variacao", sku.getVariacao() != null ? sku.getVariacao() : "");
            data.put("precoVenda", sku.getPrecoVenda());
            data.put("estoque", sku.getEstoque() != null ? sku.getEstoque().getQuantidade() : 0);

            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("success", false);
            body.put("message", messageResolver.getMessage("sku.error.not-found"));
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    /**
     * Atualiza a embalagem vinculada ao SKU
     */
    @PutMapping("/{id}/embalagem")
    public ResponseEntity<Map<String, Object>> atualizarEmbalagem(@PathVariable Long id,
                                                                  @RequestBody AtualizarEmbalagemSKURequest request) {
        var sku = skuService.atualizarEmbalagem(id, request.getEmbalagemId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "id", sku.getId(),
                        "embalagemId", sku.getEmbalagem() != null ? sku.getEmbalagem().getId() : null
                )
        ));
    }
}

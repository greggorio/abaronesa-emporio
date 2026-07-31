package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dto.EstoqueLoteDTO;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.repository.EstoqueLoteRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import com.baronesa.emporio.service.MovimentoEstoqueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/estoque")
@RequiredArgsConstructor
@Slf4j
public class EstoqueController {

    private final ProdutoSKURepository produtoSKURepository;
    private final EstoqueLoteRepository estoqueLoteRepository;
    private final MovimentoEstoqueService movimentoEstoqueService;
    private final MessageResolver messageResolver;

    @GetMapping("/sku/{skuId}/lotes")
    public ResponseEntity<List<EstoqueLoteDTO>> listarLotesPorSku(@PathVariable Long skuId) {
        ProdutoSKU sku = produtoSKURepository.findById(skuId)
                .orElseThrow(() -> new RuntimeException("SKU não encontrado: " + skuId));

        List<EstoqueLoteDTO> lotes = estoqueLoteRepository.findByProdutoSkuIdOrderByDataValidadeAscNullsLast(sku.getId())
                .stream()
                .map(el -> EstoqueLoteDTO.builder()
                        .id(el.getId())
                        .lote(el.getLote())
                        .dataValidade(el.getDataValidade())
                        .quantidade(el.getQuantidade())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(lotes);
    }

    /**
     * Zera o estoque de todos os produtos
     * Esta operação cria um movimento de estoque para cada SKU que possui estoque > 0
     */
    @GetMapping("/zerar-estoque")
    public ResponseEntity<Map<String, Object>> zerarTodoEstoque() {
        try {
            log.info("Iniciando operação de zerar todo o estoque");

            Map<String, Object> resultado = movimentoEstoqueService.zerarTodoEstoque();

            log.info("Operação de zerar estoque concluída com sucesso. Produtos afetados: {}",
                    resultado.get("produtosAfetados"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", resultado,
                    "message", messageResolver.getMessage("estoque.zerar.success",
                            new Object[]{resultado.get("produtosAfetados")})
            ));

        } catch (Exception e) {
            log.error("Erro ao zerar estoque: ", e);

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("estoque.zerar.error",
                            new Object[]{e.getMessage()}),
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Zera o estoque de um produto específico
     */
    @PostMapping("/zerar-estoque/{skuId}")
    public ResponseEntity<Map<String, Object>> zerarEstoqueProduto(@PathVariable Long skuId,
                                                                   @RequestParam String motivo) {
        try {
            log.info("Iniciando operação de zerar estoque do SKU: {}", skuId);

            var movimento = movimentoEstoqueService.zerarEstoque(skuId, motivo);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", movimento,
                    "message", messageResolver.getMessage("estoque.zerar.produto.success")
            ));

        } catch (Exception e) {
            log.error("Erro ao zerar estoque do SKU {}: ", skuId, e);

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("estoque.zerar.produto.error",
                            new Object[]{e.getMessage()}),
                    "error", e.getMessage()
            ));
        }
    }
}

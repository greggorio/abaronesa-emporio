package com.baronesa.emporio.controller.lookup;

import com.baronesa.emporio.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller responsável pelos endpoints de lookup de produtos
 * Separa as responsabilidades de lookup do controller principal
 */
@Slf4j
@RestController
@RequestMapping("/api/produtos/lookup")
@RequiredArgsConstructor
public class ProdutoLookupController {

    private final ProdutoService produtoService;

    /**
     * Endpoint de busca para lookup
     * Busca por código interno, nome ou código de barras (nos SKUs)
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam(required = false) String search) {
        try {
            List<Map<String, Object>> produtos = produtoService.buscarParaLookup(search);
            return ResponseEntity.ok(produtos);
        } catch (Exception e) {
            log.error("Erro ao buscar produtos para lookup: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    /**
     * Busca produto por ID no formato lookup
     * Necessário para o componente LookupSelect carregar o label quando há valor pré-selecionado
     */
    @GetMapping("/search/{id}")
    public ResponseEntity<Map<String, Object>> searchById(@PathVariable Long id) {
        try {
            Map<String, Object> produto = produtoService.buscarPorIdParaLookup(id);
            if (produto != null) {
                return ResponseEntity.ok(produto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Erro ao buscar produto por ID para lookup: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/by-fornecedor")
    public ResponseEntity<List<Map<String, Object>>> buscarPorFornecedor(@RequestParam String cnpj) {
        try {
            List<Map<String, Object>> produtos = produtoService.buscarProdutosPorFornecedorCnpj(cnpj);
            return ResponseEntity.ok(produtos);
        } catch (Exception e) {
            log.error("Erro ao buscar produtos por fornecedor (CNPJ={}): {}", cnpj, e.getMessage());
            return ResponseEntity.badRequest().body(List.of());
        }
    }


    /**
     * Endpoint de opções para dropdowns simples
     * Retorna lista simplificada sem todas as informações do lookup
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> buscarOptions() {
        try {
            List<Map<String, Object>> options = produtoService.buscarOptions();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", options
            ));
        } catch (Exception e) {
            log.error("Erro ao buscar opções de produtos: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar opções",
                    "error", e.getMessage()
            ));
        }
    }
}
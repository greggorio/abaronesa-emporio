package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.EmbalagemDTO;
import com.baronesa.emporio.dto.EmbalagemRequest;
import com.baronesa.emporio.repository.EmbalagemRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import com.baronesa.emporio.service.EmbalagemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/embalagens")
@RequiredArgsConstructor
public class EmbalagemController {

    private final EmbalagemService embalagemService;
    private final ProdutoSKURepository skuRepository;
    private final EmbalagemRepository embalagemRepository;

    @GetMapping
    public ResponseEntity<List<EmbalagemDTO>> listar(@RequestParam Long produtoId) {
        return ResponseEntity.ok(embalagemService.listarPorProduto(produtoId));
    }

    @GetMapping("/by-sku/{skuId}")
    public ResponseEntity<List<Map<String, Object>>> listarPorSku(@PathVariable Long skuId) {
        var sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
        var list = embalagemRepository.findByProdutoId(sku.getProduto().getId());
        List<Map<String, Object>> options = list.stream()
                .map(e -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("value", e.getId());
                    map.put("label", e.getNome() + (e.getFatorBase() != null ? " (" + e.getFatorBase() + ")" : ""));
                    return map;
                })
                .toList();
        return ResponseEntity.ok(options);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmbalagemDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(embalagemService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EmbalagemDTO> criar(@RequestBody EmbalagemRequest request) {
        EmbalagemDTO dto = embalagemService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmbalagemDTO> atualizar(@PathVariable Long id, @RequestBody EmbalagemRequest request) {
        EmbalagemDTO dto = embalagemService.atualizar(id, request);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        embalagemService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

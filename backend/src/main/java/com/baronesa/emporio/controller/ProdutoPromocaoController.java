package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ProdutoPromocaoDTO;
import com.baronesa.emporio.dto.ProdutoPromocaoRequest;
import com.baronesa.emporio.service.ProdutoPromocaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/produto-promocao")
@RequiredArgsConstructor
public class ProdutoPromocaoController {

    private final ProdutoPromocaoService service;

    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<List<ProdutoPromocaoDTO>> listarPorProduto(@PathVariable Long produtoId) {
        return ResponseEntity.ok(service.listarPorProduto(produtoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoPromocaoDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProdutoPromocaoDTO> criar(@RequestBody ProdutoPromocaoRequest request) {
        ProdutoPromocaoDTO dto = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoPromocaoDTO> atualizar(@PathVariable Long id, @RequestBody ProdutoPromocaoRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ProdutoDisponibilidadeDTO;
import com.baronesa.emporio.dto.ProdutoDisponibilidadeRequest;
import com.baronesa.emporio.service.ProdutoDisponibilidadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produto-disponibilidade")
@RequiredArgsConstructor
public class ProdutoDisponibilidadeController {

    private final ProdutoDisponibilidadeService service;

    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<List<ProdutoDisponibilidadeDTO>> listarPorProduto(@PathVariable Long produtoId) {
        return ResponseEntity.ok(service.listarPorProduto(produtoId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDisponibilidadeDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProdutoDisponibilidadeDTO> criar(@RequestBody ProdutoDisponibilidadeRequest request) {
        ProdutoDisponibilidadeDTO dto = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDisponibilidadeDTO> atualizar(@PathVariable Long id, @RequestBody ProdutoDisponibilidadeRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

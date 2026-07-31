package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.SubcategoriaDisponibilidadeDTO;
import com.baronesa.emporio.dto.SubcategoriaDisponibilidadeRequest;
import com.baronesa.emporio.service.SubcategoriaDisponibilidadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subcategoria-disponibilidade")
@RequiredArgsConstructor
public class SubcategoriaDisponibilidadeController {

    private final SubcategoriaDisponibilidadeService service;

    @GetMapping("/subcategoria/{subcategoriaId}")
    public ResponseEntity<List<SubcategoriaDisponibilidadeDTO>> listarPorSubcategoria(@PathVariable Long subcategoriaId) {
        return ResponseEntity.ok(service.listarPorSubcategoria(subcategoriaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubcategoriaDisponibilidadeDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<SubcategoriaDisponibilidadeDTO> criar(@RequestBody SubcategoriaDisponibilidadeRequest request) {
        SubcategoriaDisponibilidadeDTO dto = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubcategoriaDisponibilidadeDTO> atualizar(@PathVariable Long id, @RequestBody SubcategoriaDisponibilidadeRequest request) {
        return ResponseEntity.ok(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ProdutoHarmonizacaoDTO;
import com.baronesa.emporio.service.ProdutoHarmonizacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos/{produtoPrincipalId}/harmonizacoes")
@RequiredArgsConstructor
public class ProdutoHarmonizacaoController {

    private final ProdutoHarmonizacaoService produtoHarmonizacaoService;

    @PostMapping
    public ResponseEntity<ProdutoHarmonizacaoDTO> criarHarmonizacao(
            @PathVariable Long produtoPrincipalId,
            @RequestBody ProdutoHarmonizacaoDTO dto) {
        ProdutoHarmonizacaoDTO harmonizacao = produtoHarmonizacaoService.criarHarmonizacao(produtoPrincipalId, dto);
        return new ResponseEntity<>(harmonizacao, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProdutoHarmonizacaoDTO>> listarHarmonizacoes(
            @PathVariable Long produtoPrincipalId) {
        List<ProdutoHarmonizacaoDTO> harmonizacoes = produtoHarmonizacaoService.listarHarmonizacoes(produtoPrincipalId);
        return ResponseEntity.ok(harmonizacoes);
    }

    @DeleteMapping("/{harmonizacaoId}")
    public ResponseEntity<Void> removerHarmonizacao(
            @PathVariable Long produtoPrincipalId,
            @PathVariable Long harmonizacaoId) {
        produtoHarmonizacaoService.removerHarmonizacao(produtoPrincipalId, harmonizacaoId);
        return ResponseEntity.noContent().build();
    }
}

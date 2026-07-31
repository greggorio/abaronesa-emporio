package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.FichaTecnicaDTO;
import com.baronesa.emporio.dto.FichaTecnicaRequest;
import com.baronesa.emporio.service.FichaTecnicaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ficha-tecnica")
@RequiredArgsConstructor
public class FichaTecnicaController {

    private final FichaTecnicaService fichaTecnicaService;

    @GetMapping("/produto/{produtoId}")
    public ResponseEntity<FichaTecnicaDTO> buscarPorProduto(@PathVariable Long produtoId) {
        try {
            FichaTecnicaDTO ficha = fichaTecnicaService.buscarPorProduto(produtoId);
            return ResponseEntity.ok(ficha);
        } catch (Exception e) {
            log.error("Erro ao buscar ficha técnica do produto {}: {}", produtoId, e.getMessage());
            throw new RuntimeException("Erro ao buscar ficha técnica: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<FichaTecnicaDTO> salvar(@RequestBody FichaTecnicaRequest request) {
        try {
            FichaTecnicaDTO ficha = fichaTecnicaService.salvar(request);
            return ResponseEntity.ok(ficha);
        } catch (Exception e) {
            log.error("Erro ao salvar ficha técnica: {}", e.getMessage());
            throw new RuntimeException("Erro ao salvar ficha técnica: " + e.getMessage());
        }
    }

    @GetMapping("/produto/{produtoId}/custo")
    public ResponseEntity<Map<String, Object>> calcularCusto(@PathVariable Long produtoId) {
        try {
            BigDecimal custo = fichaTecnicaService.calcularCusto(produtoId);
            return ResponseEntity.ok(Map.of(
                    "produtoId", produtoId,
                    "custoTotal", custo
            ));
        } catch (Exception e) {
            log.error("Erro ao calcular custo do produto {}: {}", produtoId, e.getMessage());
            throw new RuntimeException("Erro ao calcular custo: " + e.getMessage());
        }
    }

    @GetMapping("/buscar-insumos")
    public ResponseEntity<java.util.List<Map<String, Object>>> buscarInsumosDisponiveis(
            @RequestParam(required = false) String search) {
        try {
            java.util.List<Map<String, Object>> insumos = fichaTecnicaService.buscarInsumosDisponiveis(search);
            return ResponseEntity.ok(insumos);
        } catch (Exception e) {
            log.error("Erro ao buscar insumos: {}", e.getMessage());
            throw new RuntimeException("Erro ao buscar insumos: " + e.getMessage());
        }
    }
}

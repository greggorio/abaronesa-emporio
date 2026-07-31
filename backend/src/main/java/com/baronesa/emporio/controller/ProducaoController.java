package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dto.ProducaoDTO;
import com.baronesa.emporio.dto.ProducaoRequest;
import com.baronesa.emporio.service.ProducaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/producao")
@RequiredArgsConstructor
public class ProducaoController {

    private final ProducaoService producaoService;
    private final MessageResolver messageResolver;

    @PostMapping
    public ResponseEntity<Map<String, Object>> produzir(@RequestBody ProducaoRequest request) {
        try {
            ProducaoDTO producao = producaoService.produzir(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", producao,
                    "message", messageResolver.getMessage("producao.success.created")
            ));
        } catch (Exception e) {
            log.error("Erro ao registrar produção: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage(),
                    "error", e.getMessage()
            ));
        }
    }
}

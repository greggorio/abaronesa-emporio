package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.GrupoClienteDescontoDTO;
import com.baronesa.emporio.dto.GrupoClienteDescontoRequest;
import com.baronesa.emporio.service.GrupoClienteDescontoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos-clientes/{grupoClienteId}/descontos")
@RequiredArgsConstructor
public class GrupoClienteDescontoController {

    private final GrupoClienteDescontoService descontoService;

    @GetMapping
    public ResponseEntity<List<GrupoClienteDescontoDTO>> listar(@PathVariable Long grupoClienteId) {
        return ResponseEntity.ok(descontoService.listar(grupoClienteId));
    }

    @PutMapping
    public ResponseEntity<List<GrupoClienteDescontoDTO>> salvar(
            @PathVariable Long grupoClienteId,
            @RequestBody @Valid List<GrupoClienteDescontoRequest> requests
    ) {
        return ResponseEntity.ok(descontoService.salvar(grupoClienteId, requests));
    }
}

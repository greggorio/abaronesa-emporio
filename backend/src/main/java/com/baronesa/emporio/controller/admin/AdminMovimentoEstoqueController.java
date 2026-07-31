package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.EstornarMovimentoEstoqueRequest;
import com.baronesa.emporio.dto.EstornarMovimentoEstoqueResponse;
import com.baronesa.emporio.service.MovimentoEstoqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/estoque/movimentos")
@RequiredArgsConstructor
public class AdminMovimentoEstoqueController {

    private final MovimentoEstoqueService movimentoEstoqueService;

    @PostMapping("/{movimentoId}/estornar")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
    public ResponseEntity<EstornarMovimentoEstoqueResponse> estornarMovimento(
            @PathVariable Long movimentoId,
            @RequestBody(required = false) EstornarMovimentoEstoqueRequest request) {
        
        if (request == null) {
            request = EstornarMovimentoEstoqueRequest.builder()
                    .observacao("")
                    .motivo("")
                    .build();
        }
        
        EstornarMovimentoEstoqueResponse response = movimentoEstoqueService.estornarMovimento(movimentoId, request);
        
        if (!response.isSucesso()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
}
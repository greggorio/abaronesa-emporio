package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.entity.Recompensa;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.service.RecompensaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/recompensas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
public class RecompensaAdminController {

    private final RecompensaService recompensaService;

    @GetMapping
    public ResponseEntity<List<Recompensa>> getAll() {
        List<Recompensa> recompensas = recompensaService.getAll();
        return ResponseEntity.ok(recompensas);
    }

    @PostMapping
    public ResponseEntity<Recompensa> create(@RequestBody Recompensa recompensa) {
        Recompensa criada = recompensaService.create(recompensa);
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Recompensa> update(@PathVariable Long id, @RequestBody Recompensa recompensa) {
        Recompensa atualizada = recompensaService.update(id, recompensa);
        return ResponseEntity.ok(atualizada);
    }
}

package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.BirthdayNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/birthday")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
public class BirthdayNotificationController {

    private final BirthdayNotificationService birthdayNotificationService;

    @PostMapping("/dispatch")
    public ResponseEntity<String> dispatchBirthdayNotifications(
            @RequestParam String tipo,
            @RequestParam(required = false) Integer diasAntes) {
        
        try {
            birthdayNotificationService.sendBirthdayNotifications(tipo, diasAntes);
            return ResponseEntity.ok("Notificações de aniversário enviadas com sucesso para o tipo: " + tipo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificações de aniversário: " + e.getMessage());
        }
    }
}
package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.dashboard.ConsumoVoucherDTO;
import com.baronesa.emporio.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard/voucher-consumo")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
public class AdminVoucherDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/mes-atual")
    public ResponseEntity<Map<String, Object>> listarConsumoMesAtual() {
        List<ConsumoVoucherDTO> data = dashboardService.getConsumoVoucherFuncionariosMesAtual();
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDateTime inicio = inicioMes.atStartOfDay();
        LocalDateTime fim = inicio.plusMonths(1);
        return ResponseEntity.ok(Map.of(
                "inicio", inicio,
                "fim", fim,
                "data", data
        ));
    }
}

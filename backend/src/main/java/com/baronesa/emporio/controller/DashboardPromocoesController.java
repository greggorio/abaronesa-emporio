package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.DashboardPromocoesDTO;
import com.baronesa.emporio.service.DashboardPromocoesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/promocoes")
@CrossOrigin(origins = "*")
public class DashboardPromocoesController {

    @Autowired
    private DashboardPromocoesService dashboardPromocoesService;

    @GetMapping
    public ResponseEntity<DashboardPromocoesDTO> getDashboardPromocoes(
            @RequestParam(name = "periodo", defaultValue = "7d") String periodo) {
        DashboardPromocoesDTO response = dashboardPromocoesService.getDashboardPromocoes(periodo);
        return ResponseEntity.ok(response);
    }
}

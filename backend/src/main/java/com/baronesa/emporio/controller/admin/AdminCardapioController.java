package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.AdminCardapioProdutoDTO;
import com.baronesa.emporio.service.CardapioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cardapio")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM','WAITER','CAIXA')")
public class AdminCardapioController {

    private final CardapioService cardapioService;

    @GetMapping("/v2")
    public ResponseEntity<Map<String, Object>> buscarCardapioAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(value = "q", required = false) String query
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("nome").ascending());
        Page<AdminCardapioProdutoDTO> result = cardapioService.buscarProdutosAdminPaginado(query, pageable);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}

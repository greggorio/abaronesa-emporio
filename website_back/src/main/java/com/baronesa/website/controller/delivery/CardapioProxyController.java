package com.baronesa.website.controller.delivery;

import com.baronesa.website.service.delivery.ErpCardapioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/cardapio")
@RequiredArgsConstructor
public class CardapioProxyController {

    private final ErpCardapioClient cardapioClient;

    @GetMapping("/v2")
    public ResponseEntity<List<ErpCardapioClient.CardapioCategoria>> cardapioV2() {
        return ResponseEntity.ok(cardapioClient.fetchCardapioV2());
    }
}

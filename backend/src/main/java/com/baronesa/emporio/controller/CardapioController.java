package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.CardapioCategoriaDTO;
import com.baronesa.emporio.dto.CardapioProdutoDTO;
import com.baronesa.emporio.dto.CardapioCategoriaV2DTO;
import com.baronesa.emporio.service.CardapioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/cardapio")
@RequiredArgsConstructor
public class CardapioController {

    private final CardapioService cardapioService;

    /**
     * Endpoint público para buscar o cardápio completo.
     * Retorna apenas categorias e produtos com exibirNoCardapio = true,
     * ordenados pelos campos ordem e nome.
     */
    @GetMapping
    public ResponseEntity<List<CardapioCategoriaDTO>> buscarCardapio(
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        List<CardapioCategoriaDTO> cardapio = cardapioService.buscarCardapioCompleto();
        return ResponseEntity.ok(cardapio);
    }

    /**
     * Endpoint público para buscar produtos em destaque.
     * Retorna até 6 produtos com destaque = true e exibirNoCardapio = true.
     * Se houver menos de 6 em destaque, completa com produtos normais.
     */
    @GetMapping("/destaques")
    public ResponseEntity<List<CardapioProdutoDTO>> buscarProdutosDestaque(
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        List<CardapioProdutoDTO> destaques = cardapioService.buscarProdutosDestaque();
        return ResponseEntity.ok(destaques);
    }

    /**
     * Endpoint público v2 com SKUs por produto
     */
    @GetMapping("/v2")
    public ResponseEntity<List<CardapioCategoriaV2DTO>> buscarCardapioV2(
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        List<CardapioCategoriaV2DTO> cardapio = cardapioService.buscarCardapioCompletoV2(guestToken);
        return ResponseEntity.ok(cardapio);
    }

    /**
     * Endpoint público específico para delivery.
     * Por enquanto replica o comportamento do v2 e pode receber regras próprias depois.
     */
    @GetMapping("/delivery")
    public ResponseEntity<List<CardapioCategoriaV2DTO>> buscarCardapioDelivery(
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        List<CardapioCategoriaV2DTO> cardapio = cardapioService.buscarCardapioCompletoV2Delivery(guestToken);
        return ResponseEntity.ok(cardapio);
    }
}

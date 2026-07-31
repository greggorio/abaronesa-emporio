package com.baronesa.website.service.delivery;

import com.baronesa.website.config.ErpConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCardapioClient {

    private final ErpConfig erpConfig;
    private final WebClient webClient = WebClient.builder().build();

    public List<CardapioCategoria> fetchCardapioV2() {
        String url = erpConfig.getApiUrl() + "/api/public/cardapio/v2";
        return webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(CardapioCategoria.class)
                .collectList()
                .block(Duration.ofSeconds(10));
    }

    @Data
    public static class CardapioCategoria {
        private Long id;
        private String nome;
        private List<CardapioProduto> produtos;
    }

    @Data
    public static class CardapioProduto {
        private Long id;
        private String nome;
        private BigDecimal preco;
        private BigDecimal preco_promocional;
        private BigDecimal precoPromocional;
        private List<CardapioSku> skus;
    }

    @Data
    public static class CardapioSku {
        private Long id;
        private String variacao;
        private BigDecimal preco;
        private BigDecimal precoPromocional;
    }
}

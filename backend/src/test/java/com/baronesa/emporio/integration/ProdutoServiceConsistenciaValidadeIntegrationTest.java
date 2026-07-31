package com.baronesa.emporio.integration;

import com.baronesa.emporio.dto.ProdutoDTO;
import com.baronesa.emporio.dto.ProdutoRequest;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.entity.Estoque;
import com.baronesa.emporio.entity.EstoqueLote;
import com.baronesa.emporio.enums.UnidadeBase;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.service.ProdutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de Integração - ProdutoService - Consistência de Validade")
class ProdutoServiceConsistenciaValidadeIntegrationTest {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoSKURepository produtoSKURepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private EstoqueLoteRepository estoqueLoteRepository;

    @Nested
    @DisplayName("Caso B30 - Criar produto sem controlaEstoque nem controlaValidade")
    class CasoB30 {
        @Test
        @DisplayName(" deve persistir ambos como true por default")
        void devePersistirDefaultsTrue() {
            ProdutoRequest request = ProdutoRequest.builder()
                    .nome("Produto Teste Default")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .build();

            ProdutoDTO dto = produtoService.criar(request);

            assertNotNull(dto);
            assertTrue(dto.getControlaEstoque(), "controlaEstoque deve ser true por default");
            assertTrue(dto.getControlaValidade(), "controlaValidade deve ser true por default");
        }
    }

    @Nested
    @DisplayName("Caso B31 - Atualizar produto sem informar os dois campos")
    class CasoB31 {
        @Test
        @DisplayName(" deve manter valores atuais (default operacional)")
        void deveManterValoresAtuais() {
            ProdutoRequest createRequest = ProdutoRequest.builder()
                    .nome("Produto Teste Atualizacao")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .controlaEstoque(true)
                    .controlaValidade(true)
                    .build();

            ProdutoDTO dtoCriado = produtoService.criar(createRequest);

            ProdutoRequest updateRequest = ProdutoRequest.builder()
                    .nome("Produto Teste Atualizacao")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .build();

            ProdutoDTO dtoAtualizado = produtoService.atualizar(dtoCriado.getId(), updateRequest);

            assertTrue(dtoAtualizado.getControlaEstoque(), "controlaEstoque deve permanecer true");
            assertTrue(dtoAtualizado.getControlaValidade(), "controlaValidade deve permanecer true");
        }
    }

    @Nested
    @DisplayName("Caso B32 - Preencher vidaUtilDias")
    class CasoB32 {
        @Test
        @DisplayName(" deve forcar controlaValidade e controlaEstoque como true")
        void deveForcarCamposTrue() {
            ProdutoRequest request = ProdutoRequest.builder()
                    .nome("Produto com Vida Util")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .vidaUtilDias(15)
                    .controlaEstoque(false)
                    .controlaValidade(false)
                    .build();

            ProdutoDTO dto = produtoService.criar(request);

            assertNotNull(dto);
            assertEquals(15, dto.getVidaUtilDias());
            assertTrue(dto.getControlaValidade(), "controlaValidade deve ser true quando vidaUtilDias informado");
            assertTrue(dto.getControlaEstoque(), "controlaEstoque deve ser true quando controlaValidade true");
        }
    }

    @Nested
    @DisplayName("Caso B33 - Desligar validade em produto com historico de lotes")
    class CasoB33 {
        @Test
        @DisplayName(" deve impedir desativacao de controlaValidade")
        void deveImpedirDesativacao() {
            ProdutoRequest createRequest = ProdutoRequest.builder()
                    .nome("Produto com Lotes")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .controlaEstoque(true)
                    .controlaValidade(true)
                    .build();

            ProdutoDTO dtoCriado = produtoService.criar(createRequest);

            ProdutoSKU sku = produtoSKURepository.findById(dtoCriado.getSkus().get(0).getId()).orElseThrow();
            EstoqueLote lote = EstoqueLote.builder()
                    .produtoSku(sku)
                    .lote("LOTE-LEGADO")
                    .dataValidade(LocalDate.now().plusDays(10))
                    .quantidade(new BigDecimal("5"))
                    .build();
            lote.setCreatedAt(LocalDateTime.now());
            lote.setUpdatedAt(LocalDateTime.now());
            estoqueLoteRepository.save(lote);

            ProdutoRequest updateRequest = ProdutoRequest.builder()
                    .nome("Produto com Lotes")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .controlaEstoque(true)
                    .controlaValidade(false)
                    .build();

            assertThrows(Exception.class, () -> {
                produtoService.atualizar(dtoCriado.getId(), updateRequest);
            });
        }
    }

    @Nested
    @DisplayName("Caso B34 - Rejeitar vidaUtilDias <= 0")
    class CasoB34 {
        @Test
        @DisplayName(" deve rejeitar vidaUtilDias negativo")
        void deveRejeitarVidaUtilNegativa() {
            ProdutoRequest request = ProdutoRequest.builder()
                    .nome("Produto Vida Util Negativa")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .vidaUtilDias(-5)
                    .build();

            assertThrows(Exception.class, () -> {
                produtoService.criar(request);
            });
        }

        @Test
        @DisplayName(" deve rejeitar vidaUtilDias zero")
        void deveRejeitarVidaUtilZero() {
            ProdutoRequest request = ProdutoRequest.builder()
                    .nome("Produto Vida Util Zero")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .vidaUtilDias(0)
                    .build();

            assertThrows(Exception.class, () -> {
                produtoService.criar(request);
            });
        }
    }
}

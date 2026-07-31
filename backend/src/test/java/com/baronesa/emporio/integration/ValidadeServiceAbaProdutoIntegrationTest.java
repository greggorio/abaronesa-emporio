package com.baronesa.emporio.integration;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.enums.UnidadeBase;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.service.ValidadeService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de Integração - ValidadeService - Aba por Produto")
class ValidadeServiceAbaProdutoIntegrationTest {

    @Autowired
    private ValidadeService validadeService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoSKURepository produtoSKURepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private EstoqueLoteRepository estoqueLoteRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Autowired
    private MovimentoEstoqueLoteRepository movimentoEstoqueLoteRepository;

    private Produto produtoA;
    private ProdutoSKU skuA1;
    private Estoque estoqueA1;

    @BeforeEach
    void setUp() {
        produtoA = Produto.builder()
                .nome("Produto Teste A - Controle Completo")
                .unidadeMedida(UnidadeMedida.UN)
                .unidadeBase(UnidadeBase.UNIDADE)
                .controlaEstoque(true)
                .controlaValidade(true)
                .vidaUtilDias(30)
                .ativo(true)
                .vendavel(true)
                .build();
        produtoA = produtoRepository.save(produtoA);

        skuA1 = ProdutoSKU.builder()
                .produto(produtoA)
                .sku("TEST-A-001")
                .variacao("Único")
                .principal(true)
                .ativo(true)
                .build();
        skuA1 = produtoSKURepository.save(skuA1);

        estoqueA1 = Estoque.builder()
                .quantidade(40)
                .reservado(0)
                .estoqueMinimo(0)
                .build();
        estoqueA1 = estoqueRepository.save(estoqueA1);
        skuA1.setEstoque(estoqueA1);
        skuA1 = produtoSKURepository.save(skuA1);
    }

    @Nested
    @DisplayName("Caso B01 - Retornar lotes agrupados por SKU")
    class CasoB01 {
        @Test
        @DisplayName(" deve retornar lotes agrupados por SKU com todos os campos")
        void deveRetornarLotesAgrupadosPorSku() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(produtoA.getId(), null, null, true, true, null);

            assertNotNull(response);
            assertEquals(produtoA.getId(), response.getProdutoId());
            assertEquals(produtoA.getNome(), response.getProdutoNome());
            assertTrue(response.getControlaValidade());
            assertEquals(30, response.getVidaUtilDias());

            assertNotNull(response.getResumo());
            assertTrue(response.getResumo().getTotalSkus() > 0);
            assertTrue(response.getResumo().getTotalLotes() >= 0);

            assertNotNull(response.getSkus());
            assertFalse(response.getSkus().isEmpty());

            ValidadeProdutoSkuDTO skuDTO = response.getSkus().get(0);
            assertNotNull(skuDTO.getSkuId());
            assertNotNull(skuDTO.getSkuCodigo());
            assertNotNull(skuDTO.getSkuDescricao());
            assertNotNull(skuDTO.getEstoqueAgregado());
            assertNotNull(skuDTO.getSomaLotes());
            assertNotNull(skuDTO.getLotes());
        }

        @Test
        @DisplayName(" deve calcular divergencia entre somaLotes e estoqueAgregado")
        void deveCalcularDivergencia() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(produtoA.getId(), null, null, true, true, null);

            ValidadeProdutoSkuDTO skuDTO = response.getSkus().get(0);
            boolean hasDivergence = skuDTO.getSomaLotes().compareTo(skuDTO.getEstoqueAgregado()) != 0;
            assertEquals(hasDivergence, skuDTO.getPossuiDivergencia());
        }
    }

    @Nested
    @DisplayName("Caso B02 - Filtrar por skuId")
    class CasoB02 {
        @Test
        @DisplayName(" deve retornar apenas o SKU filtrado")
        void deveFiltrarPorSkuId() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(
                    produtoA.getId(), skuA1.getId(), null, true, true, null);

            assertNotNull(response);
            assertNotNull(response.getSkus());
            assertEquals(1, response.getSkus().size());
            assertEquals(skuA1.getId(), response.getSkus().get(0).getSkuId());
        }
    }

    @Nested
    @DisplayName("Caso B03 - Filtrar por status")
    class CasoB03 {
        @Test
        @DisplayName(" deve retornar apenas lotes do status filtrado")
        void deveFiltrarPorStatus() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(
                    produtoA.getId(), null, "OK", true, true, null);

            assertNotNull(response);
            if (!response.getSkus().isEmpty() && !response.getSkus().get(0).getLotes().isEmpty()) {
                response.getSkus().forEach(sku -> {
                    sku.getLotes().forEach(lote -> {
                        assertEquals("OK", lote.getStatus());
                    });
                });
            }
        }
    }

    @Nested
    @DisplayName("Caso B04 - Filtrar somenteComSaldo")
    class CasoB04 {
        @Test
        @DisplayName(" deve excluir lotes zerados quando somenteComSaldo=true")
        void deveExcluirLotesZerados() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO responseComSaldo = validadeService.getLotesPorProduto(
                    produtoA.getId(), null, null, true, true, null);

            ValidadeProdutoLotesDTO responseTodos = validadeService.getLotesPorProduto(
                    produtoA.getId(), null, null, false, true, null);

            int totalComSaldo = responseComSaldo.getSkus().stream()
                    .mapToInt(sku -> sku.getLotes().size())
                    .sum();
            int totalTodos = responseTodos.getSkus().stream()
                    .mapToInt(sku -> sku.getLotes().size())
                    .sum();

            assertTrue(totalComSaldo <= totalTodos);
        }
    }

    @Nested
    @DisplayName("Caso B05 - Filtrar incluirSemLote")
    class CasoB05 {
        @Test
        @DisplayName(" deve excluir SEM_LOTE quando incluirSemLote=false")
        void deveExcluirSemLote() {
            criarLotesDeTeste();

            ValidadeProdutoLotesDTO responseInclui = validadeService.getLotesPorProduto(
                    produtoA.getId(), null, null, true, true, null);

            ValidadeProdutoLotesDTO responseExclui = validadeService.getLotesPorProduto(
                    produtoA.getId(), null, null, true, false, null);

            boolean temSemLoteIncluido = responseInclui.getSkus().stream()
                    .flatMap(sku -> sku.getLotes().stream())
                    .anyMatch(lote -> "SEM_LOTE".equals(lote.getRastreabilidade()));

            boolean temSemLoteExcluido = responseExclui.getSkus().stream()
                    .flatMap(sku -> sku.getLotes().stream())
                    .anyMatch(lote -> "SEM_LOTE".equals(lote.getRastreabilidade()));

            if (temSemLoteIncluido) {
                assertFalse(temSemLoteExcluido);
            }
        }
    }

    @Nested
    @DisplayName("Casos B10-B17 - Comandos de Lote")
    class ComandosLote {
        @Test
        @DisplayName("B10: deve criar lote manual com sucesso")
        void deveCriarLoteManual() {
            CriarLoteProdutoValidadeRequest request = CriarLoteProdutoValidadeRequest.builder()
                    .skuId(skuA1.getId())
                    .lote("LOTE-TEST-001")
                    .dataValidade(LocalDate.now().plusDays(15))
                    .quantidade(new BigDecimal("10"))
                    .observacao("Teste de criacao")
                    .build();

            ValidadeProdutoLoteDTO response = validadeService.criarLotePorProduto(produtoA.getId(), request);

            assertNotNull(response);
            assertNotNull(response.getEstoqueLoteId());
            assertEquals("LOTE-TEST-001", response.getLote());
            assertEquals(0, new BigDecimal("10").compareTo(response.getQuantidade()));

            List<MovimentoEstoqueLote> auditoria = movimentoEstoqueLoteRepository.findByEstoqueLoteIdOrderByMovimentoDesc(response.getEstoqueLoteId());
            assertFalse(auditoria.isEmpty());
        }

        @Test
        @DisplayName("B11: deve impedir criar lote para SKU de outro produto")
        void deveImpedirCriarLoteParaSkuDeOutroProduto() {
            Produto outroProduto = Produto.builder()
                    .nome("Outro Produto")
                    .unidadeMedida(UnidadeMedida.UN)
                    .unidadeBase(UnidadeBase.UNIDADE)
                    .controlaEstoque(true)
                    .controlaValidade(true)
                    .ativo(true)
                    .build();
            outroProduto = produtoRepository.save(outroProduto);

            ProdutoSKU outroSku = ProdutoSKU.builder()
                    .produto(outroProduto)
                    .sku("OUTRO-001")
                    .variacao("Único")
                    .ativo(true)
                    .build();
            outroSku = produtoSKURepository.save(outroSku);

            CriarLoteProdutoValidadeRequest request = CriarLoteProdutoValidadeRequest.builder()
                    .skuId(outroSku.getId())
                    .lote("LOTE-TEST")
                    .quantidade(new BigDecimal("5"))
                    .build();

            assertThrows(Exception.class, () -> {
                validadeService.criarLotePorProduto(produtoA.getId(), request);
            });
        }

        @Test
        @DisplayName("B12: deve impedir criar lote com quantidade nao positiva")
        void deveImpedirCriarLoteComQuantidadeInvalida() {
            CriarLoteProdutoValidadeRequest request = CriarLoteProdutoValidadeRequest.builder()
                    .skuId(skuA1.getId())
                    .lote("LOTE-TEST")
                    .quantidade(BigDecimal.ZERO)
                    .build();

            assertThrows(Exception.class, () -> {
                validadeService.criarLotePorProduto(produtoA.getId(), request);
            });
        }

        @Test
        @DisplayName("B13: deve executar ajuste SET corretamente")
        void deveExecutarAjusteSET() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            AjusteEstoqueLoteRequest request = AjusteEstoqueLoteRequest.builder()
                    .acao("SET")
                    .quantidade(new BigDecimal("25"))
                    .observacao("Ajuste SET")
                    .build();

            ValidadeProdutoLoteDTO response = validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), request);

            assertNotNull(response);
            assertEquals(0, new BigDecimal("25").compareTo(response.getQuantidade()));
        }

        @Test
        @DisplayName("B14: deve executar ajuste ADD corretamente")
        void deveExecutarAjusteADD() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();
            BigDecimal quantidadeInicial = loteCriado.getQuantidade();

            AjusteEstoqueLoteRequest request = AjusteEstoqueLoteRequest.builder()
                    .acao("ADD")
                    .quantidade(new BigDecimal("5"))
                    .observacao("Ajuste ADD")
                    .build();

            ValidadeProdutoLoteDTO response = validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), request);

            BigDecimal esperado = quantidadeInicial.add(new BigDecimal("5"));
            assertEquals(0, esperado.compareTo(response.getQuantidade()));
        }

        @Test
        @DisplayName("B15: deve executar ajuste REMOVE corretamente")
        void deveExecutarAjusteREMOVE() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();
            BigDecimal quantidadeInicial = loteCriado.getQuantidade();

            AjusteEstoqueLoteRequest request = AjusteEstoqueLoteRequest.builder()
                    .acao("REMOVE")
                    .quantidade(new BigDecimal("3"))
                    .observacao("Ajuste REMOVE")
                    .build();

            ValidadeProdutoLoteDTO response = validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), request);

            BigDecimal esperado = quantidadeInicial.subtract(new BigDecimal("3"));
            assertEquals(0, esperado.compareTo(response.getQuantidade()));
        }

        @Test
        @DisplayName("B16: deve impedir ajuste que leve lote a quantidade negativa")
        void deveImpedirAjusteNegativo() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            AjusteEstoqueLoteRequest request = AjusteEstoqueLoteRequest.builder()
                    .acao("REMOVE")
                    .quantidade(new BigDecimal("100"))
                    .observacao("Teste negativo")
                    .build();

            assertThrows(Exception.class, () -> {
                validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), request);
            });
        }

        @Test
        @DisplayName("B17: deve zerar lote corretamente")
        void deveZerarLote() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            ZerarEstoqueLoteRequest request = ZerarEstoqueLoteRequest.builder()
                    .observacao("Zerar teste")
                    .build();

            ValidadeProdutoLoteDTO response = validadeService.zerarLote(loteCriado.getEstoqueLoteId(), request);

            assertNotNull(response);
            assertEquals(0, BigDecimal.ZERO.compareTo(response.getQuantidade()));
        }
    }

    @Nested
    @DisplayName("Casos B20-B22 - Historico por Lote")
    class HistoricoLote {
        @Test
        @DisplayName("B20: deve listar historico do lote em ordem decrescente")
        void deveListarHistoricoOrdenado() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            List<ValidadeProdutoLoteMovimentoDTO> historico = validadeService.listarMovimentosLote(loteCriado.getEstoqueLoteId());

            assertNotNull(historico);

            if (historico.size() > 1) {
                for (int i = 0; i < historico.size() - 1; i++) {
                    assertTrue(historico.get(i).getDataMovimento().compareTo(historico.get(i + 1).getDataMovimento()) >= 0);
                }
            }
        }

        @Test
        @DisplayName("B21: deve normalizar delta de venda como negativo")
        void deveNormalizarDeltaVenda() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            BigDecimal quantidadeInicial = loteCriado.getQuantidade();

            AjusteEstoqueLoteRequest request = AjusteEstoqueLoteRequest.builder()
                    .acao("REMOVE")
                    .quantidade(new BigDecimal("2"))
                    .build();
            validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), request);

            List<ValidadeProdutoLoteMovimentoDTO> historico = validadeService.listarMovimentosLote(loteCriado.getEstoqueLoteId());

            boolean temMovimentoNegativo = historico.stream()
                    .anyMatch(m -> m.getDeltaQuantidade().compareTo(BigDecimal.ZERO) < 0);
            assertTrue(temMovimentoNegativo);
        }

        @Test
        @DisplayName("B22: deve mostrar ajuste manual com sinal correto")
        void deveMostrarAjusteComSinalCorreto() {
            ValidadeProdutoLoteDTO loteCriado = criarLoteInicial();

            AjusteEstoqueLoteRequest requestAdd = AjusteEstoqueLoteRequest.builder()
                    .acao("ADD")
                    .quantidade(new BigDecimal("5"))
                    .observacao("Teste ADD")
                    .build();
            validadeService.ajustarLote(loteCriado.getEstoqueLoteId(), requestAdd);

            List<ValidadeProdutoLoteMovimentoDTO> historico = validadeService.listarMovimentosLote(loteCriado.getEstoqueLoteId());

            boolean temMovimentoPositivo = historico.stream()
                    .anyMatch(m -> m.getDeltaQuantidade().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(temMovimentoPositivo);
        }
    }

    private void criarLotesDeTeste() {
        EstoqueLote lote1 = EstoqueLote.builder()
                .produtoSku(skuA1)
                .lote("LOTE-A")
                .dataValidade(LocalDate.now().plusDays(5))
                .quantidade(new BigDecimal("2"))
                .build();
        lote1.setCreatedAt(LocalDateTime.now());
        lote1.setUpdatedAt(LocalDateTime.now());
        estoqueLoteRepository.save(lote1);

        EstoqueLote lote2 = EstoqueLote.builder()
                .produtoSku(skuA1)
                .lote("LOTE-B")
                .dataValidade(LocalDate.now().plusDays(20))
                .quantidade(new BigDecimal("3"))
                .build();
        lote2.setCreatedAt(LocalDateTime.now());
        lote2.setUpdatedAt(LocalDateTime.now());
        estoqueLoteRepository.save(lote2);

        EstoqueLote lote3 = EstoqueLote.builder()
                .produtoSku(skuA1)
                .lote(EstoqueLote.DEFAULT_LOTE)
                .dataValidade(EstoqueLote.DEFAULT_DATA_VALIDADE)
                .quantidade(new BigDecimal("38"))
                .build();
        lote3.setCreatedAt(LocalDateTime.now());
        lote3.setUpdatedAt(LocalDateTime.now());
        estoqueLoteRepository.save(lote3);
    }

    private ValidadeProdutoLoteDTO criarLoteInicial() {
        CriarLoteProdutoValidadeRequest request = CriarLoteProdutoValidadeRequest.builder()
                .skuId(skuA1.getId())
                .lote("LOTE-TEST-INICIAL")
                .dataValidade(LocalDate.now().plusDays(10))
                .quantidade(new BigDecimal("10"))
                .observacao("Lote inicial para testes")
                .build();

        return validadeService.criarLotePorProduto(produtoA.getId(), request);
    }
}

package com.baronesa.emporio.integration;

import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.enums.UnidadeBase;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.repository.*;
import com.baronesa.emporio.service.MovimentoEstoqueService;
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
@DisplayName("Teste Integrado - FEFO após Venda")
class FefoVendaIntegrationTest {

    @Autowired
    private ValidadeService validadeService;

    @Autowired
    private MovimentoEstoqueService movimentoEstoqueService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoSKURepository produtoSKURepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private EstoqueLoteRepository estoqueLoteRepository;

    @Autowired
    private MovimentoEstoqueLoteRepository movimentoEstoqueLoteRepository;

    private Produto produtoFefo;
    private ProdutoSKU skuFefo;
    private Estoque estoqueFefo;

    @BeforeEach
    void setUp() {
        produtoFefo = Produto.builder()
                .nome("Produto FEFO Teste")
                .unidadeMedida(UnidadeMedida.UN)
                .unidadeBase(UnidadeBase.UNIDADE)
                .controlaEstoque(true)
                .controlaValidade(true)
                .vidaUtilDias(30)
                .ativo(true)
                .vendavel(true)
                .build();
        produtoFefo = produtoRepository.save(produtoFefo);

        skuFefo = ProdutoSKU.builder()
                .produto(produtoFefo)
                .sku("FEFO-TEST-001")
                .variacao("Único")
                .principal(true)
                .ativo(true)
                .build();
        skuFefo = produtoSKURepository.save(skuFefo);

        estoqueFefo = Estoque.builder()
                .quantidade(0)
                .reservado(0)
                .estoqueMinimo(0)
                .build();
        estoqueFefo = estoqueRepository.save(estoqueFefo);
        skuFefo.setEstoque(estoqueFefo);
        skuFefo = produtoSKURepository.save(skuFefo);
    }

    @Nested
    @DisplayName("Caso B40 - Venda baixa primeiro o lote com validade mais próxima")
    class CasoB40 {
        @Test
        @DisplayName(" deve consumir lote com validade mais próxima primeiro")
        void deveConsumirLoteMaisProximo() {
            EstoqueLote loteA = criarLoteComEstoque("LOTE-A", LocalDate.now().plusDays(5), new BigDecimal("2"));
            EstoqueLote loteB = criarLoteComEstoque("LOTE-B", LocalDate.now().plusDays(20), new BigDecimal("3"));

            Integer estoqueAnterior = estoqueFefo.getQuantidade();

            MovimentoEstoqueRequest vendaRequest = MovimentoEstoqueRequest.builder()
                    .skuId(skuFefo.getId())
                    .tipoMovimento(TipoMovimentoEstoque.VENDA.getCodigo())
                    .quantidade(new BigDecimal("3"))
                    .observacao("Venda teste FEFO")
                    .build();

            movimentoEstoqueService.movimentarEstoque(vendaRequest);

            EstoqueLote loteAActualizado = estoqueLoteRepository.findById(loteA.getId()).orElseThrow();
            EstoqueLote loteBAtualizado = estoqueLoteRepository.findById(loteB.getId()).orElseThrow();

            assertEquals(0, new BigDecimal("0").compareTo(loteAActualizado.getQuantidade()),
                    "Lote A (validade mais próxima) deve estar zerado");
            assertEquals(0, new BigDecimal("2").compareTo(loteBAtualizado.getQuantidade()),
                    "Lote B deve ter 2 restantes");
        }

        @Test
        @DisplayName(" deve refletir consumo no histórico dos lotes")
        void deveRegistrarHistoricoConsumo() {
            EstoqueLote loteA = criarLoteComEstoque("LOTE-A", LocalDate.now().plusDays(5), new BigDecimal("2"));
            EstoqueLote loteB = criarLoteComEstoque("LOTE-B", LocalDate.now().plusDays(20), new BigDecimal("3"));

            MovimentoEstoqueRequest vendaRequest = MovimentoEstoqueRequest.builder()
                    .skuId(skuFefo.getId())
                    .tipoMovimento(TipoMovimentoEstoque.VENDA.getCodigo())
                    .quantidade(new BigDecimal("3"))
                    .observacao("Venda teste FEFO")
                    .build();

            movimentoEstoqueService.movimentarEstoque(vendaRequest);

            List<MovimentoEstoqueLote> historicoLoteA = movimentoEstoqueLoteRepository
                    .findByEstoqueLoteIdOrderByMovimentoDesc(loteA.getId());
            List<MovimentoEstoqueLote> historicoLoteB = movimentoEstoqueLoteRepository
                    .findByEstoqueLoteIdOrderByMovimentoDesc(loteB.getId());

            assertFalse(historicoLoteA.isEmpty(), "Lote A deve ter histórico");
            assertFalse(historicoLoteB.isEmpty(), "Lote B deve ter histórico");

            boolean temConsumoLoteA = historicoLoteA.stream()
                    .anyMatch(m -> m.getMovimentoEstoque().getTipoMovimento().equals(TipoMovimentoEstoque.VENDA.getCodigo()));
            boolean temConsumoLoteB = historicoLoteB.stream()
                    .anyMatch(m -> m.getMovimentoEstoque().getTipoMovimento().equals(TipoMovimentoEstoque.VENDA.getCodigo()));

            assertTrue(temConsumoLoteA, "Lote A deve ter movimento de venda");
            assertTrue(temConsumoLoteB, "Lote B deve ter movimento de venda");
        }
    }

    @Nested
    @DisplayName("Caso B41 - Consulta com somenteComSaldo=true após venda")
    class CasoB41 {
        @Test
        @DisplayName(" deve retornar apenas lotes com saldo após venda")
        void deveExcluirLotesZerados() {
            criarLoteComEstoque("LOTE-A", LocalDate.now().plusDays(5), new BigDecimal("2"));
            criarLoteComEstoque("LOTE-B", LocalDate.now().plusDays(20), new BigDecimal("3"));

            MovimentoEstoqueRequest vendaRequest = MovimentoEstoqueRequest.builder()
                    .skuId(skuFefo.getId())
                    .tipoMovimento(TipoMovimentoEstoque.VENDA.getCodigo())
                    .quantidade(new BigDecimal("3"))
                    .observacao("Venda teste FEFO")
                    .build();

            movimentoEstoqueService.movimentarEstoque(vendaRequest);

            ValidadeProdutoLotesDTO responseComSaldo = validadeService.getLotesPorProduto(
                    produtoFefo.getId(), null, null, true, true, null);

            ValidadeProdutoLotesDTO responseTodos = validadeService.getLotesPorProduto(
                    produtoFefo.getId(), null, null, false, true, null);

            int totalComSaldo = responseComSaldo.getSkus().stream()
                    .mapToInt(sku -> sku.getLotes().size())
                    .sum();
            int totalTodos = responseTodos.getSkus().stream()
                    .mapToInt(sku -> sku.getLotes().size())
                    .sum();

            assertTrue(totalComSaldo < totalTodos,
                    "Com somenteComSaldo=true deve haver menos lotes");
        }

        @Test
        @DisplayName(" deve mostrar reflexo da venda na aba de validade")
        void deveMostrarReflexoVendaNaAba() {
            criarLoteComEstoque("LOTE-A", LocalDate.now().plusDays(5), new BigDecimal("2"));
            criarLoteComEstoque("LOTE-B", LocalDate.now().plusDays(20), new BigDecimal("3"));

            BigDecimal somaLotesAntes = estoqueLoteRepository.findByProdutoSkuIdOrderByDataValidadeAscNullsLast(skuFefo.getId())
                    .stream()
                    .map(l -> l.getQuantidade() == null ? BigDecimal.ZERO : l.getQuantidade())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            MovimentoEstoqueRequest vendaRequest = MovimentoEstoqueRequest.builder()
                    .skuId(skuFefo.getId())
                    .tipoMovimento(TipoMovimentoEstoque.VENDA.getCodigo())
                    .quantidade(new BigDecimal("3"))
                    .observacao("Venda teste FEFO")
                    .build();

            movimentoEstoqueService.movimentarEstoque(vendaRequest);

            ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(
                    produtoFefo.getId(), null, null, false, true, null);

            BigDecimal somaLotesDepois = response.getSkus().get(0).getSomaLotes();

            BigDecimal esperado = somaLotesAntes.subtract(new BigDecimal("3"));
            assertEquals(0, esperado.compareTo(somaLotesDepois),
                    "Soma dos lotes deve refletir a baixa por FEFO");
        }
    }

    private EstoqueLote criarLoteComEstoque(String lote, LocalDate dataValidade, BigDecimal quantidade) {
        EstoqueLote loteEntity = EstoqueLote.builder()
                .produtoSku(skuFefo)
                .lote(lote)
                .dataValidade(dataValidade)
                .quantidade(quantidade)
                .build();
        loteEntity.setCreatedAt(LocalDateTime.now());
        loteEntity.setUpdatedAt(LocalDateTime.now());
        return estoqueLoteRepository.save(loteEntity);
    }
}

package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.DashboardPedidosPreparacaoDTO;
import com.baronesa.emporio.dto.DashboardVendasDTO;
import com.baronesa.emporio.dto.PedidosLocalDTO;
import com.baronesa.emporio.dto.ProdutoDesempenhoDTO;
import com.baronesa.emporio.dto.dashboard.MovimentoCaixaResumoDTO;
import com.baronesa.emporio.dto.dashboard.VendasPeriodoDTO;
import com.baronesa.emporio.dto.dashboard.FinanceiroResumoDTO;
import com.baronesa.emporio.dto.dashboard.PendenciasDTO;
import com.baronesa.emporio.dto.dashboard.VendasHistoricoDTO;
import com.baronesa.emporio.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/pedidos-preparacao")
    public ResponseEntity<DashboardPedidosPreparacaoDTO> getPedidosPreparacao() {
        log.info("Requisição para buscar pedidos em preparação");
        DashboardPedidosPreparacaoDTO metricas = dashboardService.getPedidosPreparacao();
        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/vendas")
    public ResponseEntity<DashboardVendasDTO> getVendas() {
        log.info("Requisição para buscar métricas de vendas");
        DashboardVendasDTO metricas = dashboardService.getVendas();
        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/top-produtos")
    public ResponseEntity<List<ProdutoDesempenhoDTO>> getTopProdutos(
            @RequestParam(defaultValue = "valor") String ordenarPor,
            @RequestParam(defaultValue = "7d") String periodo
    ) {
        log.info("Requisição para buscar top produtos ordenados por: {} no período: {}", ordenarPor, periodo);
        List<ProdutoDesempenhoDTO> produtos = dashboardService.getTopProdutos(ordenarPor, periodo);
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/movimento-caixa")
    public ResponseEntity<MovimentoCaixaResumoDTO> getMovimentoCaixa() {
        log.info("Requisição para buscar movimento de caixa do dia");
        try {
            MovimentoCaixaResumoDTO movimento = dashboardService.getMovimentoCaixa();
            return ResponseEntity.ok(movimento);
        } catch (Exception e) {
            log.error("Erro ao buscar movimento de caixa", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/vendas-periodo")
    public ResponseEntity<VendasPeriodoDTO> getVendasPeriodo(
            @RequestParam(defaultValue = "7d") String periodo,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        log.info("Requisição para buscar vendas do período: {} (custom from: {} to: {})", periodo, dateFrom, dateTo);
        try {
            VendasPeriodoDTO vendas = dashboardService.getVendasPeriodo(periodo, dateFrom, dateTo);
            return ResponseEntity.ok(vendas);
        } catch (Exception e) {
            log.error("Erro ao buscar vendas do período", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/pedidos-local")
    public ResponseEntity<PedidosLocalDTO> getPedidosPorLocal(
            @RequestParam(defaultValue = "30") Integer diasRetroativos
    ) {
        log.info("Requisição para buscar pedidos por local dos últimos {} dias", diasRetroativos);
        try {
            PedidosLocalDTO pedidos = dashboardService.getPedidosPorLocal(diasRetroativos);
            return ResponseEntity.ok(pedidos);
        } catch (Exception e) {
            log.error("Erro ao buscar pedidos por local", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/financeiro")
    public ResponseEntity<FinanceiroResumoDTO> getFinanceiro() {
        log.info("Requisição para buscar resumo financeiro");
        try {
            FinanceiroResumoDTO financeiro = dashboardService.getFinanceiro();
            return ResponseEntity.ok(financeiro);
        } catch (Exception e) {
            log.error("Erro ao buscar resumo financeiro", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/pendencias")
    public ResponseEntity<PendenciasDTO> getPendencias(
            @RequestParam(name = "somenteAtivos", defaultValue = "true") boolean somenteAtivos
    ) {
        log.info("Requisição para buscar pendências de produtos (somenteAtivos={})", somenteAtivos);
        try {
            PendenciasDTO pendencias = dashboardService.getPendencias(somenteAtivos);
            return ResponseEntity.ok(pendencias);
        } catch (Exception e) {
            log.error("Erro ao buscar pendências", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/vendas-historico")
    public ResponseEntity<List<VendasHistoricoDTO>> getVendasHistorico(
            @RequestParam(defaultValue = "30d") String periodo
    ) {
        log.info("Requisição para buscar histórico de vendas para o período: {}", periodo);
        try {
            List<VendasHistoricoDTO> serie = dashboardService.getVendasHistorico(periodo);
            return ResponseEntity.ok(serie);
        } catch (Exception e) {
            log.error("Erro ao buscar histórico de vendas", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ValidadeAlertaLoteDTO;
import com.baronesa.emporio.dto.ValidadeDashboardDTO;
import com.baronesa.emporio.dto.ValidadeProdutoLoteDTO;
import com.baronesa.emporio.dto.ValidadeProdutoLoteMovimentoDTO;
import com.baronesa.emporio.dto.ValidadeProdutoLotesDTO;
import com.baronesa.emporio.dto.CriarLoteProdutoValidadeRequest;
import com.baronesa.emporio.dto.AjusteEstoqueLoteRequest;
import com.baronesa.emporio.dto.TarefaValidadeDTO;
import com.baronesa.emporio.dto.TarefaValidadeRequest;
import com.baronesa.emporio.dto.TarefaValidadeItemRequest;
import com.baronesa.emporio.dto.TarefaValidadeDivergenciaDTO;
import com.baronesa.emporio.dto.TarefaValidadeTratarRequest;
import com.baronesa.emporio.dto.ZerarEstoqueLoteRequest;
import com.baronesa.emporio.service.ValidadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/validade")
@RequiredArgsConstructor
public class ValidadeController {

    private final ValidadeService validadeService;

    @GetMapping("/alertas")
    public ResponseEntity<List<ValidadeAlertaLoteDTO>> getAlertasValidade(
            @RequestParam(required = false, defaultValue = "true") Boolean somenteComSaldo,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "200") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {

        List<ValidadeAlertaLoteDTO> alertas = validadeService.getAlertasValidade(somenteComSaldo, skuId, produtoId, status, limit, offset);
        return ResponseEntity.ok(alertas);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ValidadeDashboardDTO> getDashboardValidade(
            @RequestParam(required = false, defaultValue = "true") Boolean somenteComSaldo,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long produtoId) {

        ValidadeDashboardDTO dashboard = validadeService.getDashboard(somenteComSaldo, skuId, produtoId);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/produtos/{produtoId}/lotes")
    public ResponseEntity<ValidadeProdutoLotesDTO> getLotesPorProduto(
            @PathVariable Long produtoId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "true") Boolean somenteComSaldo,
            @RequestParam(required = false, defaultValue = "true") Boolean incluirSemLote,
            @RequestParam(required = false) String buscaLote) {

        ValidadeProdutoLotesDTO response = validadeService.getLotesPorProduto(
                produtoId,
                skuId,
                status,
                somenteComSaldo,
                incluirSemLote,
                buscaLote
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/produtos/{produtoId}/lotes")
    public ResponseEntity<ValidadeProdutoLoteDTO> criarLotePorProduto(
            @PathVariable Long produtoId,
            @RequestBody CriarLoteProdutoValidadeRequest request) {

        return ResponseEntity.ok(validadeService.criarLotePorProduto(produtoId, request));
    }

    @PostMapping("/lotes/{estoqueLoteId}/ajustar")
    public ResponseEntity<ValidadeProdutoLoteDTO> ajustarLote(
            @PathVariable Long estoqueLoteId,
            @RequestBody AjusteEstoqueLoteRequest request) {

        return ResponseEntity.ok(validadeService.ajustarLote(estoqueLoteId, request));
    }

    @PostMapping("/lotes/{estoqueLoteId}/zerar")
    public ResponseEntity<ValidadeProdutoLoteDTO> zerarLote(
            @PathVariable Long estoqueLoteId,
            @RequestBody(required = false) ZerarEstoqueLoteRequest request) {

        return ResponseEntity.ok(validadeService.zerarLote(estoqueLoteId, request));
    }

    @GetMapping("/lotes/{estoqueLoteId}/movimentos")
    public ResponseEntity<List<ValidadeProdutoLoteMovimentoDTO>> listarMovimentosLote(
            @PathVariable Long estoqueLoteId) {

        return ResponseEntity.ok(validadeService.listarMovimentosLote(estoqueLoteId));
    }

    // =========================
    // Tarefa de validade
    // =========================

    @PostMapping("/tarefas")
    public ResponseEntity<TarefaValidadeDTO> criarTarefa(@RequestBody(required = false) TarefaValidadeRequest request) {
        return ResponseEntity.ok(validadeService.criarTarefa(request));
    }

    @PostMapping("/tarefas/{id}/itens")
    public ResponseEntity<TarefaValidadeDTO> adicionarItens(@PathVariable Long id, @RequestBody(required = false) List<TarefaValidadeItemRequest> itens) {
        return ResponseEntity.ok(validadeService.adicionarItens(id, itens));
    }

    @DeleteMapping("/tarefas/{id}/itens/{itemId}")
    public ResponseEntity<TarefaValidadeDTO> removerItem(@PathVariable Long id, @PathVariable Long itemId) {
        return ResponseEntity.ok(validadeService.removerItem(id, itemId));
    }

    @PostMapping("/tarefas/{id}/finalizar")
    public ResponseEntity<TarefaValidadeDTO> finalizar(@PathVariable Long id) {
        return ResponseEntity.ok(validadeService.finalizarTarefa(id));
    }

    @GetMapping("/tarefas")
    public ResponseEntity<List<TarefaValidadeDTO>> listarTarefas() {
        return ResponseEntity.ok(validadeService.listarTarefas());
    }

    @GetMapping("/tarefas/{id}")
    public ResponseEntity<TarefaValidadeDTO> buscarTarefa(@PathVariable Long id) {
        return ResponseEntity.ok(validadeService.buscarTarefa(id));
    }

    @PutMapping("/tarefas/{id}/observacao")
    public ResponseEntity<TarefaValidadeDTO> atualizarObservacao(@PathVariable Long id, @RequestBody TarefaValidadeRequest request) {
        return ResponseEntity.ok(validadeService.atualizarObservacao(id, request));
    }

    @PostMapping("/tarefas/{id}/cancelar")
    public ResponseEntity<TarefaValidadeDTO> cancelarTarefa(@PathVariable Long id) {
        return ResponseEntity.ok(validadeService.cancelarTarefa(id));
    }

    @GetMapping("/divergencias")
    public ResponseEntity<List<TarefaValidadeDivergenciaDTO>> listarDivergencias(@RequestParam(required = false) Long tarefaId) {
        return ResponseEntity.ok(validadeService.listarDivergencias(tarefaId));
    }

    @PostMapping("/divergencias/{id}/tratar")
    public ResponseEntity<TarefaValidadeDivergenciaDTO> tratar(@PathVariable Long id, @RequestBody TarefaValidadeTratarRequest request) {
        return ResponseEntity.ok(validadeService.tratarDivergencia(id, request));
    }
}

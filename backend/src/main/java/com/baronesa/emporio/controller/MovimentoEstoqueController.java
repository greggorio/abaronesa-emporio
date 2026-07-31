package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.AjusteEstoqueRequest;
import com.baronesa.emporio.dto.MovimentoEstoqueDTO;
import com.baronesa.emporio.dto.MovimentoEstoqueRequest;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.MovimentoEstoqueService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.MovimentoEstoqueListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movimento-estoque")
@RequiredArgsConstructor
@Slf4j
public class MovimentoEstoqueController extends BaseListController<MovimentoEstoqueListService>
        implements FormConfigurableController {

    private final MovimentoEstoqueListService listService;
    private final MovimentoEstoqueService movimentoService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Serviço centralizado

    @Override
    protected MovimentoEstoqueListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "movimento-estoque";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // O endpoint /form-config vem automaticamente da interface!
    // Não precisa mais implementar aqui

    /** Criar novo movimento de estoque */
    @PostMapping
    public ResponseEntity<Map<String, Object>> criar(@RequestBody MovimentoEstoqueRequest request) {
        try {
            MovimentoEstoqueDTO movimento = movimentoService.movimentarEstoque(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", movimento,
                    "message", messageResolver.getMessage("movimento.estoque.success.created")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage(),
                    "error", e.getMessage()
            ));
        }
    }

    /** Ajuste rápido de estoque */
    @PostMapping("/ajuste-rapido")
    public ResponseEntity<Map<String, Object>> ajusteRapido(@RequestBody AjusteEstoqueRequest request) {
        try {
            MovimentoEstoqueDTO movimento = movimentoService.ajustarEstoque(
                    request.getSkuId(),
                    request.getEmbalagemId(),
                    request.getQuantidade(),
                    request.getMotivo(),
                    request.isAdicionar()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", movimento,
                    "message", messageResolver.getMessage("movimento.estoque.success.ajuste")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("movimento.estoque.error.ajuste", new Object[]{e.getMessage()}),
                    "error", e.getMessage()
            ));
        }
    }

    /** Buscar movimento por ID */
    @GetMapping("/{id}")
    public ResponseEntity<MovimentoEstoqueDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(movimentoService.buscarPorId(id));
    }

    /** Listar movimentos por SKU */
    @GetMapping("/sku/{skuId}")
    public ResponseEntity<Map<String, Object>> listarPorSku(@PathVariable Long skuId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "50") int size) {
        var movimentos = movimentoService.listarPorSku(skuId, page, size);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", movimentos.getContent(),
                "totalElements", movimentos.getTotalElements(),
                "totalPages", movimentos.getTotalPages()
        ));
    }

    /** Configuração do dialog de ajuste rápido */
    @GetMapping("/ajuste-dialog")
    public ResponseEntity<Map<String, Object>> getAjusteDialogConfig() {
        return ResponseEntity.ok(Map.of(
                "title", messageResolver.getMessage("movimento.estoque.dialog.ajuste.title"),
                "fields", List.of(
                        Map.of(
                                "name", "skuId",
                                "label", messageResolver.getMessage("movimento.estoque.field.sku"),
                                "component", "q-select",
                                "cols", "col-12",
                                "required", true,
                                "optionsEndpoint", "/api/skus/search-options",
                                "props", Map.of(
                                        "use-input", true,
                                        "input-debounce", 300,
                                        "option-label", "label",
                                        "option-value", "value",
                                        "emit-value", true,
                                        "map-options", true
                                )
                        ),
                        Map.of(
                                "name", "embalagemId",
                                "label", "Embalagem",
                                "component", "q-select",
                                "cols", "col-12",
                                "required", false,
                                "dependsOn", "skuId",
                                "dependsOnEndpoint", "/api/embalagens/by-sku/{value}",
                                "props", Map.of(
                                        "option-label", "label",
                                        "option-value", "value",
                                        "emit-value", true,
                                        "map-options", true
                                )
                        ),
                        Map.of(
                                "name", "operacao",
                                "label", messageResolver.getMessage("movimento.estoque.field.operacao"),
                                "component", "q-radio-group",
                                "cols", "col-12",
                                "required", true,
                                "options", List.of(
                                        Map.of("label", messageResolver.getMessage("movimento.estoque.option.adicionar"), "value", "adicionar"),
                                        Map.of("label", messageResolver.getMessage("movimento.estoque.option.remover"), "value", "remover")
                                )
                        ),
                        Map.of(
                                "name", "quantidade",
                                "label", messageResolver.getMessage("movimento.estoque.field.quantidade"),
                                "component", "q-input",
                                "cols", "col-12",
                                "required", true,
                                "props", Map.of(
                                        "type", "number",
                                        "min", 1,
                                        "step", 1
                                )
                        ),
                        Map.of(
                                "name", "motivo",
                                "label", messageResolver.getMessage("movimento.estoque.field.motivo"),
                                "component", "q-input",
                                "cols", "col-12",
                                "required", true,
                                "props", Map.of(
                                        "type", "textarea",
                                        "rows", 3,
                                        "placeholder", messageResolver.getMessage("movimento.estoque.placeholder.motivo")
                                )
                        )
                )
        ));
    }

    /** Endpoint para entrada de mercadoria (futuro) */
    @PostMapping("/entrada")
    public ResponseEntity<Map<String, Object>> registrarEntrada(@RequestBody MovimentoEstoqueRequest request) {
        // Forçar tipo como ENTRADA
        request.setTipoMovimento(1); // TipoMovimentoEstoque.ENTRADA
        return criar(request);
    }

    /** Relatório de movimentos por período */
    @GetMapping("/relatorio")
    public ResponseEntity<Map<String, Object>> relatorioMovimentos(
            @RequestParam String dataInicio,
            @RequestParam String dataFim,
            @RequestParam(required = false) Integer tipoMovimento) {

        var relatorio = movimentoService.gerarRelatorio(dataInicio, dataFim, tipoMovimento);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", relatorio
        ));
    }
}

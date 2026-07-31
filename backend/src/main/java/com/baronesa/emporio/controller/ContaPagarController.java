package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.ContaPagarDTO;
import com.baronesa.emporio.dto.ContaPagarRequest;
import com.baronesa.emporio.service.ContaPagarService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.ContaPagarListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/contas-pagar")
@RequiredArgsConstructor
@Slf4j
public class ContaPagarController extends BaseListController<ContaPagarListService>
        implements FormConfigurableController {

    private final ContaPagarListService listService;
    private final ContaPagarService contaPagarService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected ContaPagarListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "contas-pagar";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    /* ----------------------------------------------------------------
     *  Endpoints CRUD
     * -------------------------------------------------------------- */

    @PostMapping
    public ResponseEntity<ContaPagarDTO> criar(@RequestBody ContaPagarRequest request) {
        return ResponseEntity.ok(contaPagarService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaPagarDTO> editar(@PathVariable Long id, @RequestBody ContaPagarRequest request) {
        return ResponseEntity.ok(contaPagarService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaPagarService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaPagarDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(contaPagarService.buscarPorId(id));
    }

    /* ----------------------------------------------------------------
     *  Endpoints específicos de Contas a Pagar
     * -------------------------------------------------------------- */

    @PostMapping("/parcela/{parcelaId}/pagar")
    public ResponseEntity<Map<String, Object>> pagarParcela(
            @PathVariable Long parcelaId,
            @RequestParam LocalDate dataPagamento,
            @RequestParam String formaPagamento
    ) {
        contaPagarService.pagarParcela(parcelaId, dataPagamento, formaPagamento);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Parcela paga com sucesso"
        ));
    }

    @PostMapping("/parcela/{parcelaId}/cancelar-pagamento")
    public ResponseEntity<Map<String, Object>> cancelarPagamentoParcela(@PathVariable Long parcelaId) {
        contaPagarService.cancelarPagamentoParcela(parcelaId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pagamento cancelado com sucesso"
        ));
    }
}

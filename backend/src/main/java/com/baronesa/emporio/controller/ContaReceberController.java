package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.ContaReceberDTO;
import com.baronesa.emporio.dto.ContaReceberRequest;
import com.baronesa.emporio.dto.RecebimentoHojeDTO;
import com.baronesa.emporio.service.ContaReceberService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.ContaReceberListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/contas-receber")
@RequiredArgsConstructor
@Slf4j
public class ContaReceberController extends BaseListController<ContaReceberListService>
        implements FormConfigurableController {

    private final ContaReceberListService listService;
    private final ContaReceberService contaReceberService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected ContaReceberListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "contas-receber";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // O endpoint /form-config agora vem automaticamente da interface FormConfigurableController
    // com paginação incluída! Não precisa mais implementar aqui.

    /* ----------------------------------------------------------------
     *  Endpoints CRUD
     * -------------------------------------------------------------- */

    @PostMapping
    public ResponseEntity<ContaReceberDTO> criar(@RequestBody ContaReceberRequest request) {
        return ResponseEntity.ok(contaReceberService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaReceberDTO> editar(@PathVariable Long id, @RequestBody ContaReceberRequest request) {
        return ResponseEntity.ok(contaReceberService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaReceberService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaReceberDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(contaReceberService.buscarPorId(id));
    }

    /* ----------------------------------------------------------------
     *  Endpoints específicos de Contas a Receber
     * -------------------------------------------------------------- */

    @PostMapping("/parcela/{parcelaId}/receber")
    public ResponseEntity<Map<String, Object>> receberParcela(
            @PathVariable Long parcelaId,
            @RequestParam LocalDate dataRecebimento,
            @RequestParam String formaRecebimento,
            @RequestParam BigDecimal valorRecebido
    ) {
        contaReceberService.receberParcela(parcelaId, dataRecebimento, formaRecebimento, valorRecebido);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", messageResolver.getMessage("contaReceber.success.parcela-recebida")
        ));
    }

    @GetMapping("/getrecebimentoshoje")
    public ResponseEntity<List<RecebimentoHojeDTO>> getRecebimentosHoje() {
        try {
            List<RecebimentoHojeDTO> recebimentos = contaReceberService.buscarRecebimentosHoje();
            return ResponseEntity.ok(recebimentos);
        } catch (Exception e) {
            log.error("Erro ao buscar recebimentos de hoje", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/parcela/{parcelaId}/marcar-cobranca")
    public ResponseEntity<Map<String, Object>> marcarCobrancaEnviada(@PathVariable Long parcelaId) {
        contaReceberService.marcarCobrancaEnviada(parcelaId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", messageResolver.getMessage("contaReceber.success.cobranca-marcada")
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> minhasContas() {
        return ResponseEntity.ok(contaReceberService.buscarMinhasContas());
    }
}
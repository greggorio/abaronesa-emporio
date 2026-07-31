package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.Fornecedor;
import com.baronesa.emporio.repository.RecebimentoMercadoriaRepository;
import com.baronesa.emporio.service.FornecedorService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.RecebimentoMercadoriaService;
import com.baronesa.emporio.service.NFImportService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.RecebimentoMercadoriaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/recebimentos")
@RequiredArgsConstructor
public class RecebimentoMercadoriaController extends BaseListController<RecebimentoMercadoriaListService>
        implements FormConfigurableController {

    private final RecebimentoMercadoriaListService listService;
    private final RecebimentoMercadoriaService recebimentoService;
    private final RecebimentoMercadoriaRepository recebimentoMercadoriaRepository;
    private final FornecedorService fornecedorService;
    private final NFImportService nfImportService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected RecebimentoMercadoriaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "recebimentos";
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

    // Buscar por ID
    @GetMapping("/{id}")
    public ResponseEntity<RecebimentoDTO> buscarPorId(@PathVariable Long id) {
        try {
            RecebimentoDTO recebimento = recebimentoService.buscarPorId(id);
            return ResponseEntity.ok(recebimento);
        } catch (Exception e) {
            log.error("Erro ao buscar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // Criar novo recebimento
    @PostMapping
    public ResponseEntity<Map<String, Object>> criar(@Valid @RequestBody RecebimentoRequest request) {
        try {
            RecebimentoDTO recebimento = recebimentoService.criar(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", recebimento,
                    "message", messageResolver.getMessage("recebimento.success.created")
            ));
        } catch (Exception e) {
            log.error("Erro ao criar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.create", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    // Editar recebimento
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id,
                                                      @Valid @RequestBody RecebimentoRequest request) {
        try {
            RecebimentoDTO recebimento = recebimentoService.editar(id, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", recebimento,
                    "message", messageResolver.getMessage("recebimento.success.updated")
            ));
        } catch (Exception e) {
            log.error("Erro ao editar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.update", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/verificar-nfe")
    public ResponseEntity<Map<String, Object>> verificarExistenciaNfe(
            @RequestParam String numeroNf,
            @RequestParam String cnpj
    ) {
        Optional<Fornecedor> fornecedorOpt = fornecedorService.buscarPorCnpj(cnpj);

        if (fornecedorOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Fornecedor com CNPJ informado não encontrado"
            ));
        }

        Fornecedor fornecedor = fornecedorOpt.get();
        boolean existe = recebimentoMercadoriaRepository.existsByFornecedorAndNumeroNf(fornecedor, numeroNf);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "exists", existe,
                "idFornecedor", fornecedor.getId()
        ));
    }

    // Deletar recebimento
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletar(@PathVariable Long id) {
        try {
            recebimentoService.deletar(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", messageResolver.getMessage("recebimento.success.deleted")
            ));
        } catch (Exception e) {
            log.error("Erro ao deletar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.delete", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    // Finalizar recebimento
    @PostMapping("/{id}/finalizar")
    public ResponseEntity<Map<String, Object>> finalizar(@PathVariable Long id) {
        try {
            RecebimentoDTO recebimento = recebimentoService.finalizar(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", recebimento,
                    "message", messageResolver.getMessage("recebimento.success.finalized")
            ));
        } catch (Exception e) {
            log.error("Erro ao finalizar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.finalize", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    // Cancelar recebimento
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(@PathVariable Long id) {
        try {
            RecebimentoDTO recebimento = recebimentoService.cancelar(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", recebimento,
                    "message", messageResolver.getMessage("recebimento.success.canceled")
            ));
        } catch (Exception e) {
            log.error("Erro ao cancelar recebimento: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.cancel", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    // Importar/Parse NF-e
    @PostMapping("/parse-nfe")
    public ResponseEntity<Map<String, Object>> parseNfe(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            ImportacaoNfeDTO resultado = nfImportService.parseXml(arquivo);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", resultado,
                    "message", messageResolver.getMessage("recebimento.success.nfe-parsed")
            ));
        } catch (Exception e) {
            log.error("Erro ao processar NF-e: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", messageResolver.getMessage("recebimento.error.parse-nfe", e.getMessage()),
                    "error", e.getMessage()
            ));
        }
    }

    // Buscar opções para dropdown
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> buscarOptions() {
        try {
            List<Map<String, Object>> options = recebimentoService.buscarOptions();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", options
            ));
        } catch (Exception e) {
            log.error("Erro ao buscar opções: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar opções",
                    "error", e.getMessage()
            ));
        }
    }

    // Endpoint de busca para produtos (usado pelo LOOKUP)
    @GetMapping("/produtos/search")
    public ResponseEntity<List<Map<String, Object>>> buscarProdutos(@RequestParam String search) {
        // Este endpoint deve ser movido para ProdutoController
        // Aqui apenas como exemplo
        return ResponseEntity.ok(List.of());
    }

    // Endpoint de busca para fornecedores (usado pelo LOOKUP)
    @GetMapping("/fornecedores/search")
    public ResponseEntity<List<Map<String, Object>>> buscarFornecedores(@RequestParam String search) {
        // Este endpoint deve ser movido para FornecedorController
        // Aqui apenas como exemplo
        return ResponseEntity.ok(List.of());
    }
}
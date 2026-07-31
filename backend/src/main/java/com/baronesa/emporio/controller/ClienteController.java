package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.ClienteService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.ClienteListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Slf4j
public class ClienteController extends BaseListController<ClienteListService>
        implements FormConfigurableController {

    private final ClienteListService listService;
    private final ClienteService clienteService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected ClienteListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "clientes";
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
    public ResponseEntity<Void> criar(@Valid @RequestBody ClienteRequest request) {
        clienteService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody ClienteUpdateRequest request) {
        clienteService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/{id}/dto")
    public ResponseEntity<ClienteDTO> buscarClienteDTO(@PathVariable Long id) {
        ClienteDTO dto = clienteService.buscarPorId(id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<ClienteOptionDTO> options = clienteService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    @GetMapping("/optionscliente")
    public ResponseEntity<Map<String, Object>> listarOptionsCliente() {
        List<ClienteOptionDTO> options = clienteService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    /* ----------------------------------------------------------------
     *  Endpoints específicos
     * -------------------------------------------------------------- */

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetarSenha(@PathVariable Long id) {
        String senhaTemporaria = gerarSenhaTemporaria();
        clienteService.alterarSenha(id, senhaTemporaria);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", messageResolver.getMessage("cliente.success.password-reset"),
                "temporaryPassword", senhaTemporaria
        ));
    }

    private String gerarSenhaTemporaria() {
        return "Temp@" + System.currentTimeMillis() % 100000;
    }

    /**
     * Busca dados do cliente autenticado para processo de checkout
     * @return DTO com todos os dados necessários para finalização de compra
     */
    @GetMapping("/me")
    public ResponseEntity<ClienteCheckoutDTO> buscarClienteAutenticado() {
        try {
            // Obter usuário autenticado
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
                log.error("Usuário não autenticado ou principal inválido");
                return ResponseEntity.status(401).build();
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            Long usuarioId = userPrincipal.getId();

            log.info("Buscando dados do cliente autenticado - ID: {}", usuarioId);

            ClienteCheckoutDTO cliente = clienteService.buscarClienteParaCheckout(usuarioId);

            return ResponseEntity.ok(cliente);

        } catch (RuntimeException e) {
            log.error("Erro ao buscar dados do cliente autenticado: {}", e.getMessage());
            return ResponseEntity.status(404).build();
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar cliente autenticado", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Busca dados do cliente pelo email para checkout sem login
     * @param email Email do cliente
     * @return DTO com dados do cliente se existir
     */
    @GetMapping("/by-email/{email}")
    public ResponseEntity<ClienteCheckoutDTO> buscarClientePorEmail(@PathVariable String email) {
        try {
            log.info("Buscando cliente por email para checkout: {}", email);
            
            ClienteCheckoutDTO cliente = clienteService.buscarClientePorEmailParaCheckout(email);
            
            if (cliente == null) {
                log.info("Cliente não encontrado para email: {}", email);
                return ResponseEntity.notFound().build();
            }
            
            log.info("Cliente encontrado para email: {} - ID: {}", email, cliente.id());
            return ResponseEntity.ok(cliente);
            
        } catch (Exception e) {
            log.error("Erro ao buscar cliente por email: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}
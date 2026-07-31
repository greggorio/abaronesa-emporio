package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.MovimentoCaixaDTO;
import com.baronesa.emporio.dto.MovimentoCaixaRequest;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.MovimentoCaixaService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.MovimentoCaixaListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/movimento-caixa")
@RequiredArgsConstructor
public class MovimentoCaixaController extends BaseListController<MovimentoCaixaListService>
        implements FormConfigurableController {

    private final MovimentoCaixaListService listService;
    private final MovimentoCaixaService service;
    private final UsuarioRepository usuarioRepository;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected MovimentoCaixaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "movimento-caixa";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    @PostMapping("/manual")
    public ResponseEntity<MovimentoCaixaDTO> registrarMovimentoManual(@RequestBody MovimentoCaixaRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();

        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new RuntimeException("Usuário não autenticado: " + principal);
        }

        Usuario vendedor = usuarioRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Vendedor não encontrado"));

        MovimentoCaixaDTO dto = service.registrarMovimento(request, vendedor);
        return ResponseEntity.ok(dto);
    }
}
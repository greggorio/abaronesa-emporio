package com.baronesa.emporio.controller.base;

import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Interface para controllers que precisam do endpoint /form-config
 * Usa default method para evitar duplicação de código
 */
public interface FormConfigurableController {

    /**
     * @return O tipo da entidade (ex: "movimento-estoque", "produtos")
     */
    String getEntityType();

    /**
     * @return O serviço de listagem da entidade
     */
    BaseListService<?> getListService();

    /**
     * @return O serviço de configuração de formulário (injetado)
     */
    FormConfigService getFormConfigService();

    /**
     * Endpoint padrão para configuração de formulário com paginação
     */
    @GetMapping("/form-config")
    default ResponseEntity<Map<String, Object>> getFormConfig(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "15") int tamanho,
            @RequestParam(required = false) String ordenacao,
            @RequestParam(required = false) String direcao,
            @RequestParam(required = false) String filter) {

        return getFormConfigService().processFormConfig(
                getEntityType(),
                getListService(),
                pagina,
                tamanho,
                ordenacao,
                direcao,
                filter
        );
    }
}
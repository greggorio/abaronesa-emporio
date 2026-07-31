package com.baronesa.emporio.controller.base;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.service.base.BaseListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Controller abstrato que entrega listagens paginadas + filtros
 * Padroniza os nomes dos query-params.
 */
public abstract class BaseListController<S extends BaseListService<?>> {

    protected abstract S getService();
    protected abstract MessageResolver getMessageResolver();

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho,
            @RequestParam(required = false) String ordenacao,
            @RequestParam(defaultValue = "asc") String direcao,
            @RequestParam(required = false) String filter) {

        Map<String, Object> body = getService()
                .list(pagina, tamanho, ordenacao, direcao, filter, getMessageResolver());

        return ResponseEntity.ok(body);
    }
}

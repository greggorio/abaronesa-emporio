package com.baronesa.emporio.controller.base;

import com.baronesa.emporio.service.lookup.GenericLookupService;
import com.baronesa.emporio.service.lookup.LookupSearchable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller base para endpoints de lookup
 *
 * @param <T> Tipo da entidade que implementa LookupSearchable
 * @param <R> Tipo do repositório
 */
public abstract class BaseLookupController<T extends LookupSearchable, R extends JpaRepository<T, Long>> {

    protected abstract R getRepository();
    protected abstract GenericLookupService getLookupService();

    /**
     * Método que pode ser sobrescrito para fornecer busca customizada
     */
    protected GenericLookupService.LookupSearchMethod<T> getSearchMethod() {
        return null;
    }

    /**
     * Endpoint de busca para lookup
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(required = false) String search) {

        List<Map<String, Object>> results = getLookupService().search(
                getRepository(),
                search,
                getSearchMethod()
        );

        return ResponseEntity.ok(results);
    }

    /**
     * Endpoint para buscar por ID (formato lookup)
     */
    @GetMapping("/search/{id}")
    public ResponseEntity<Map<String, Object>> searchById(@PathVariable Long id) {
        Map<String, Object> result = getLookupService().findById(getRepository(), id);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
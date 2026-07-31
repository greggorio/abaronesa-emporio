package com.baronesa.emporio.service;

import com.baronesa.emporio.dynamicform.config.DynamicFormRegistry;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.util.FormConfigOrderingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Serviço centralizado para manipular configurações de formulário com paginação
 * Elimina duplicação de código nos controllers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FormConfigService {

    private final DynamicFormRegistry formRegistry;
    private final FormConfigOrderingUtils orderingUtils;

    /**
     * Processa requisição de form-config com paginação
     *
     * @param entityType Tipo da entidade (ex: "movimento-estoque", "produtos")
     * @param listService Serviço de listagem específico da entidade
     * @param pagina Número da página
     * @param tamanho Tamanho da página
     * @param ordenacao Campo de ordenação (opcional)
     * @param direcao Direção da ordenação (opcional)
     * @param filter Filtro JSON (opcional)
     * @return ResponseEntity com configuração e dados paginados
     */
    public ResponseEntity<Map<String, Object>> processFormConfig(
            String entityType,
            BaseListService<?> listService,
            int pagina,
            int tamanho,
            String ordenacao,
            String direcao,
            String filter) {

        try {
            // Buscar configuração do formulário
            var config = formRegistry.getConfig(entityType);

            // Extrair ordenação padrão se não fornecida
            String[] ordering = orderingUtils.extractOrdering(config, ordenacao, direcao);

            // Buscar dados paginados
            Map<String, Object> paginationResponse;

            // Verificar se o serviço tem o método listarPaginado
            if (hasListarPaginadoMethod(listService)) {
                paginationResponse = callListarPaginado(listService, pagina, tamanho,
                        ordering[0], ordering[1], filter);
            } else {
                // Fallback para o método list padrão do BaseListService
                paginationResponse = listService.list(pagina, tamanho,
                        ordering[0], ordering[1], filter, null);
            }

            // Criar resposta base com configurações
            Map<String, Object> response = config.createResponse(List.of());

            // Sobrescrever com dados paginados
            response.putAll(paginationResponse);

            log.debug("Retornando {} registros para {}",
                    ((List<?>) response.get("table_data")).size(), entityType);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao processar form-config para {}", entityType, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", true,
                            "message", "Erro ao carregar configuração do formulário",
                            "details", e.getMessage()
                    ));
        }
    }

    /**
     * Verifica se o serviço tem o método listarPaginado
     */
    private boolean hasListarPaginadoMethod(BaseListService<?> service) {
        try {
            service.getClass().getMethod("listarPaginado",
                    int.class, int.class, String.class, String.class, String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Chama o método listarPaginado via reflection
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callListarPaginado(BaseListService<?> service,
                                                   int pagina, int tamanho,
                                                   String ordenacao, String direcao,
                                                   String filter) {
        try {
            var method = service.getClass().getMethod("listarPaginado",
                    int.class, int.class, String.class, String.class, String.class);
            return (Map<String, Object>) method.invoke(service, pagina, tamanho, ordenacao, direcao, filter);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao chamar listarPaginado", e);
        }
    }
}
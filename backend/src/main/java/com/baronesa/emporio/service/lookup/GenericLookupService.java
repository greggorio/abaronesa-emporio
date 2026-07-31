package com.baronesa.emporio.service.lookup;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GenericLookupService {

    private static final int DEFAULT_LIMIT = 50;

    /**
     * Busca genérica para lookups
     *
     * @param repository O repositório JPA da entidade
     * @param searchTerm Termo de busca
     * @param searchMethod Método customizado de busca no repositório (opcional)
     * @return Lista de resultados formatados para o lookup
     */
    public <T extends LookupSearchable> List<Map<String, Object>> search(
            JpaRepository<T, Long> repository,
            String searchTerm,
            LookupSearchMethod<T> searchMethod) {

        List<T> results;

        // Se houver método customizado de busca, usar ele
        if (searchMethod != null && searchTerm != null && !searchTerm.trim().isEmpty()) {
            results = searchMethod.search(searchTerm);
            // Limitar resultados
            if (results.size() > DEFAULT_LIMIT) {
                results = results.subList(0, DEFAULT_LIMIT);
            }
        } else {
            // Busca padrão: buscar todos e filtrar em memória
            Pageable pageable = PageRequest.of(0, DEFAULT_LIMIT);
            results = repository.findAll(pageable).getContent();

            // Se houver termo de busca, filtrar
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                results = results.stream()
                        .filter(item -> item.matchesSearch(searchTerm))
                        .limit(DEFAULT_LIMIT)
                        .collect(Collectors.toList());
            }
        }

        // Converter para formato do lookup
        return results.stream()
                .map(this::toLookupFormat)
                .collect(Collectors.toList());
    }

    /**
     * Busca por ID específico
     *
     * @param repository O repositório JPA da entidade
     * @param id ID da entidade
     * @return Mapa com dados formatados ou null se não encontrado
     */
    public <T extends LookupSearchable> Map<String, Object> findById(
            JpaRepository<T, Long> repository,
            Long id) {

        return repository.findById(id)
                .map(this::toLookupFormat)
                .orElse(null);
    }

    /**
     * Converte entidade para formato do lookup
     */
    private <T extends LookupSearchable> Map<String, Object> toLookupFormat(T entity) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Campos padrão
        result.put("id", entity.getId());
        result.put("value", entity.getId());
        result.put("label", entity.getLookupLabel());

        // Adicionar dados extras da entidade
        result.putAll(entity.getLookupData());

        return result;
    }

    /**
     * Interface funcional para métodos customizados de busca
     */
    @FunctionalInterface
    public interface LookupSearchMethod<T> {
        List<T> search(String searchTerm);
    }
}
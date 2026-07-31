package com.baronesa.emporio.service.lookup;

import java.util.Map;

/**
 * Interface que deve ser implementada por entidades que podem ser pesquisadas via lookup
 */
public interface LookupSearchable {

    /**
     * Retorna o ID da entidade
     */
    Long getId();

    /**
     * Retorna o label principal para exibição no lookup
     */
    String getLookupLabel();

    /**
     * Retorna um mapa com todos os dados extras que devem ser retornados no lookup
     */
    Map<String, Object> getLookupData();

    /**
     * Verifica se o item corresponde ao termo de busca
     */
    default boolean matchesSearch(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return true;
        }

        String term = searchTerm.toLowerCase().trim();
        String label = getLookupLabel();

        if (label != null && label.toLowerCase().contains(term)) {
            return true;
        }

        // Verificar também nos dados extras
        for (Object value : getLookupData().values()) {
            if (value != null && value.toString().toLowerCase().contains(term)) {
                return true;
            }
        }

        return false;
    }
}
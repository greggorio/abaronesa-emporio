package com.baronesa.emporio.config.form.base;

import java.util.List;
import java.util.Map;

public interface FormConfigurable {
    List<Map<String, Object>> getFormDefinitions();
    List<Map<String, Object>> getTableColumns();

    default String getProgramName() {
        return this.getClass().getSimpleName().replace("FormConfig", "");
    }

    /**
     * Retorna o tipo da entidade (usado nas URLs e identificação)
     * Por padrão, deriva do nome da classe
     */
    default String getEntityType() {
        String className = this.getClass().getSimpleName().replace("FormConfig", "");
        // Converte CamelCase para kebab-case
        return className.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}

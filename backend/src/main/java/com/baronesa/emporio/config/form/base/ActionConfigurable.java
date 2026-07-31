package com.baronesa.emporio.config.form.base;

import java.util.List;
import java.util.Map;

public interface ActionConfigurable {
    /**
     * Retorna as definições de ações/botões para o formulário
     * @return Lista de ações configuradas
     */
    List<Map<String, Object>> getFormActions();

    /**
     * Indica se deve usar as ações padrão quando não há ações customizadas
     * @return true para usar ações padrão, false caso contrário
     */
    default boolean useDefaultActions() {
        return true;
    }
}
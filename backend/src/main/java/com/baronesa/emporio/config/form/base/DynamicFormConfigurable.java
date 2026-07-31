package com.baronesa.emporio.config.form.base;

import java.util.Map;

/**
 * Interface para configs que suportam configurações de diálogo customizadas
 */
public interface DynamicFormConfigurable {
    /**
     * Retorna as configurações do diálogo (tamanho, comportamento)
     * @return Map com width, maxWidth, maxHeight, fullscreenMobile
     */
    Map<String, Object> getDialogConfig();
}

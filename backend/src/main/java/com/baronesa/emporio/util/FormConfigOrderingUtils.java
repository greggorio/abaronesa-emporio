package com.baronesa.emporio.util;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FormConfigOrderingUtils {

    public String[] extractOrdering(BaseFormConfig config, String ordenacaoParam, String direcaoParam) {
        String ordenacao = ordenacaoParam;
        String direcao = direcaoParam;

        if (isBlank(ordenacao)) {
            String tableOrder = extractTableOrder(config);

            if (!isBlank(tableOrder)) {
                String[] parts = tableOrder.trim().split("\\s+");
                ordenacao = parts[0];

                if (isBlank(direcao) && parts.length > 1) {
                    direcao = parts[1].toLowerCase();
                }
            }

            log.debug("Usando ordenação padrão da configuração - campo: {}, direção: {}",
                    ordenacao, direcao);
        }

        if (isBlank(ordenacao)) {
            ordenacao = "id";
        }

        if (isBlank(direcao)) {
            direcao = "desc";
        }

        return new String[]{ordenacao, direcao};
    }

    private String extractTableOrder(BaseFormConfig config) {
        if (config == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = null;
            Class<?> clazz = config.getClass();

            while (clazz != null && method == null) {
                try {
                    method = clazz.getDeclaredMethod("getTableOrder");
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (method != null) {
                method.setAccessible(true);
                return (String) method.invoke(config);
            }
        } catch (Exception e) {
            log.warn("Erro ao acessar getTableOrder via reflection: {}", e.getMessage());
        }

        return null;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
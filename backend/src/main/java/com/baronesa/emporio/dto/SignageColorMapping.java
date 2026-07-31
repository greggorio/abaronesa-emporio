package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageColorMapping {
    
    /**
     * Mapeamento de elementos do template para suas origens de cor.
     * Key: nome do elemento (ex: "background", "headline", "subtitle")
     * Value: origem da cor (ex: "palette:lightMuted", "custom:#FF0000", "auto")
     */
    private Map<String, String> elementMappings;
    
    /**
     * Versão do template para qual este mapeamento foi feito.
     * Permite detectar quando o template mudou e resetar para padrão.
     */
    private String templateId;
    
    /**
     * Flag indicando se o usuário quer usar o mapeamento customizado
     * ou o padrão do template.
     */
    private Boolean useCustomMapping;
}

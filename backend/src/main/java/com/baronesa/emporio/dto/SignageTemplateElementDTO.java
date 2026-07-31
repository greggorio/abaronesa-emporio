package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageTemplateElementDTO {
    
    /**
     * Identificador único do elemento (ex: "background", "headline")
     */
    private String key;
    
    /**
     * Label exibido para o usuário (ex: "Fundo", "Título")
     */
    private String label;
    
    /**
     * Descrição do elemento
     */
    private String description;
    
    /**
     * Origem padrão da cor (ex: "palette:lightMuted", "custom:#000000")
     */
    private String defaultSource;
}

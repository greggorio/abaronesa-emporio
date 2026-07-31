package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageTemplateElementsResponseDTO {
    
    /**
     * ID do template
     */
    private String templateId;
    
    /**
     * Nome do template
     */
    private String name;
    
    /**
     * Lista de elementos configuráveis do template
     */
    private List<SignageTemplateElementDTO> elements;
}

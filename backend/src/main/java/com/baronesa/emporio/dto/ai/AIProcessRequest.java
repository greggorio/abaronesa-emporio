package com.baronesa.emporio.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para requisição de processamento de comando via IA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProcessRequest {

    /**
     * Prompt/comando do usuário
     */
    private String prompt;

    /**
     * Contexto adicional (opcional)
     */
    private String context;
}

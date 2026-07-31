package com.baronesa.emporio.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO para resposta de processamento de comando via IA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIProcessResponse {

    /**
     * Tipo do comando identificado (cliente, produto, geral, erro, etc.)
     */
    private String tipo;

    /**
     * Mensagem de retorno/feedback
     */
    private String retorno;

    /**
     * Mensagem adicional (alias para retorno)
     */
    private String mensagem;

    /**
     * Dados extraídos do comando (nome, cpf, email, etc.)
     */
    private Map<String, Object> dados;

    /**
     * Indicador de sucesso
     */
    private Boolean sucesso;

    /**
     * Mensagem de erro (se houver)
     */
    private String erro;
}

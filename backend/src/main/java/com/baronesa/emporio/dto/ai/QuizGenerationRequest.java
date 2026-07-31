package com.baronesa.emporio.dto.ai;

import lombok.Data;

/**
 * Payload para gerar questões de quiz via OpenAI.
 */
@Data
public class QuizGenerationRequest {

    /** Tema ou assunto das questões. */
    private String tema;

    /** Quantidade desejada (limitada pelo serviço). */
    private Integer quantidade;

    /** Nível de dificuldade (ex.: facil, medio, dificil). */
    private String dificuldade;

    /** Idioma desejado para as perguntas. */
    private String idioma;

    /** Categoria a ser atribuída no import do quiz. */
    private Long categoryId;

    /** Pontuação padrão para cada questão (opcional). */
    private Integer points;
}

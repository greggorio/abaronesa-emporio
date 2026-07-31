package com.baronesa.emporio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.dto.ai.QuizGenerationRequest;
import com.baronesa.emporio.dto.ai.QuizQuestionDTO;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

    private final OpenAiConfigService openAiConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_QUESTOES = 20;

    /**
     * Gera questões no formato esperado pelo import do quiz.
     */
    public List<QuizQuestionDTO> generateQuestions(QuizGenerationRequest request) throws Exception {
        if (!openAiConfigService.isEnabled()) {
            throw new IllegalStateException("Serviço OpenAI não configurado ou desabilitado");
        }

        int quantidade = sanitizeQuantidade(request.getQuantidade());

        String prompt = buildPrompt(request, quantidade);

        String aiResponse = callOpenAI(prompt);

        return parseQuestions(aiResponse, request, quantidade);
    }

    private int sanitizeQuantidade(Integer quantidade) {
        if (quantidade == null || quantidade < 1) {
            return 5;
        }
        return Math.min(quantidade, MAX_QUESTOES);
    }

    private String buildPrompt(QuizGenerationRequest req, int quantidade) {
        String tema = req.getTema() != null ? req.getTema().trim() : "tema geral";
        String dificuldade = req.getDificuldade() != null ? req.getDificuldade().trim() : "medio";
        String idioma = req.getIdioma() != null ? req.getIdioma().trim() : "pt-BR";

        return "Gere " + quantidade + " questões de múltipla escolha sobre o tema '" + tema +
                "' em " + idioma + " com dificuldade " + dificuldade + ".\n" +
                "Responda SOMENTE com um array JSON, sem texto fora do JSON.\n" +
                "Cada item deve ter: index (começando em 1), question (string), options (array com 4 alternativas), correctAnswer (índice 0-3), points (inteiro), active (true), categoryId (inteiro).\n" +
                "Não inclua imagens nem campos extras. Mantenha as strings curtas e claras.";
    }

    private String callOpenAI(String prompt) throws Exception {
        OpenAiService service = openAiConfigService.createOpenAiService();
        if (service == null) {
            throw new IllegalStateException("Serviço OpenAI não configurado");
        }

        var config = openAiConfigService.getConfig();
        // Limite específico para geração de quiz para evitar truncamento de JSON longo
        int maxTokens = Math.min(config.getMaxTokens() != null ? config.getMaxTokens() : 2000, 3000);
        if (maxTokens < 1500) {
            maxTokens = 2000; // mínimo seguro para até ~20 questões curtas
        }

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(List.of(
                        new ChatMessage("system", "Você é um assistente que sempre responde apenas com JSON válido e nada mais."),
                        new ChatMessage("user", prompt)
                ))
                .maxTokens(maxTokens)
                .temperature(0.4)
                .build();

        ChatCompletionResult result = service.createChatCompletion(request);
        return result.getChoices().get(0).getMessage().getContent().trim();
    }

    private List<QuizQuestionDTO> parseQuestions(String aiResponse, QuizGenerationRequest req, int quantidadeEsperada) throws Exception {
        // Tentar parse direto; se falhar, tentar limpar blocos ``` e extrair array
        String cleaned = aiResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```", "")
                .trim();

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            log.debug("Parse direto falhou, tentando extrair array JSON: {}", ex.getMessage());
            String extracted = extractJsonArray(cleaned);
            String sanitized = extracted.replaceAll(",\\s*]", "]");
            root = objectMapper.readTree(sanitized);
        }

        if (!root.isArray()) {
            throw new IllegalArgumentException("Resposta da IA não é um array JSON");
        }

        List<QuizQuestionDTO> questions = new ArrayList<>();
        AtomicInteger idx = new AtomicInteger(1);

        for (JsonNode node : root) {
            try {
                QuizQuestionDTO q = parseSingleQuestion(node, req, idx.getAndIncrement());
                questions.add(q);
            } catch (Exception e) {
                log.warn("Ignorando questão inválida na resposta da IA: {}", e.getMessage());
            }
        }

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma questão válida retornada pela IA");
        }

        // Ajustar para a quantidade solicitada, se a IA devolver mais/menos
        if (questions.size() > quantidadeEsperada) {
            questions = questions.subList(0, quantidadeEsperada);
        }

        return questions;
    }

    private QuizQuestionDTO parseSingleQuestion(JsonNode node, QuizGenerationRequest req, int defaultIndex) {
        String question = node.path("question").asText(null);
        if (question == null || question.isEmpty()) {
            throw new IllegalArgumentException("Questão sem enunciado");
        }

        JsonNode optionsNode = node.path("options");
        List<String> options = new ArrayList<>();
        if (optionsNode.isArray()) {
            optionsNode.forEach(opt -> options.add(opt.asText("")));
        }
        if (options.size() != 4) {
            throw new IllegalArgumentException("Opções inválidas (precisa de 4 alternativas)");
        }

        int correct = node.path("correctAnswer").asInt(0);
        if (correct < 0 || correct > 3) {
            correct = 0;
        }

        Integer points = node.has("points") ? node.path("points").asInt(req.getPoints() != null ? req.getPoints() : 10) : (req.getPoints() != null ? req.getPoints() : 10);
        Boolean active = node.has("active") ? node.path("active").asBoolean(true) : true;

        Long categoryId = req.getCategoryId();
        // Se o usuário enviou categoria, preservar; caso contrário, aceitar da IA
        if (categoryId == null && node.has("categoryId") && node.path("categoryId").isNumber()) {
            categoryId = node.path("categoryId").asLong();
        }

        Integer index = node.has("index") && node.path("index").isInt() ? node.path("index").asInt() : defaultIndex;

        return QuizQuestionDTO.builder()
                .index(index)
                .question(question)
                .options(options)
                .correctAnswer(correct)
                .points(points)
                .active(active)
                .categoryId(categoryId)
                .build();
    }

    /**
     * Extrai o primeiro array JSON bem formado de uma string.
     */
    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new IllegalArgumentException("Não foi possível encontrar array JSON na resposta da IA");
    }
}

package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ai.AIProcessRequest;
import com.baronesa.emporio.dto.ai.AIProcessResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AIAssistantService {

    private final OpenAiConfigService openAiConfigService;
    private ObjectMapper objectMapper;

    public AIAssistantService(OpenAiConfigService openAiConfigService) {
        this.openAiConfigService = openAiConfigService;
    }

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();

        if (!openAiConfigService.isEnabled()) {
            log.warn("OpenAI não configurada ou desabilitada! O serviço de IA ficará desabilitado.");
        } else {
            log.info("Serviço AI Assistant disponível com configurações dinâmicas");
        }
    }

    /**
     * Método principal para processar prompt - retorna String JSON para compatibilidade
     */
    public String processPromptLegacy(String prompt) {
        try {
            AIProcessRequest request = AIProcessRequest.builder()
                    .prompt(prompt)
                    .build();

            AIProcessResponse response = processPrompt(request);

            // Converter para JSON string
            ObjectNode jsonResponse = objectMapper.createObjectNode();
            jsonResponse.put("tipo", response.getTipo());
            jsonResponse.put("retorno", response.getRetorno());

            // Adicionar campos específicos se existirem dados
            if (response.getDados() != null && !response.getDados().isEmpty()) {
                addTypeSpecificFieldsToJson(jsonResponse, response);
            }

            return objectMapper.writeValueAsString(jsonResponse);

        } catch (Exception e) {
            log.error("Erro no processamento: ", e);
            return "{\"tipo\":\"erro\",\"retorno\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Método melhorado que retorna DTO
     */
    public AIProcessResponse processPrompt(AIProcessRequest request) {
        log.info("Processando prompt: {}", request.getPrompt());

        try {
            // Construir prompt enriquecido
            String enrichedPrompt = buildEnrichedPrompt(request.getPrompt());

            // Chamar OpenAI
            String aiResponse = callOpenAI(enrichedPrompt);

            // Parsear resposta
            AIProcessResponse response = parseAIResponse(aiResponse);

            return response;

        } catch (Exception e) {
            log.error("Erro ao processar prompt: ", e);
            return createErrorResponse(e.getMessage());
        }
    }

    private String buildEnrichedPrompt(String userPrompt) {
        StringBuilder prompt = new StringBuilder();

        // Instruções do sistema
        prompt.append("Você é um assistente especializado em processar comandos para um sistema de padaria/confeitaria.\n\n");

        // Exemplos
        prompt.append(getExemplosFormatados());

        // Regras importantes
        prompt.append("\n\nREGRAS IMPORTANTES:\n");
        prompt.append("1. SEMPRE retorne APENAS um JSON válido, sem texto adicional, sem formatação markdown\n");
        prompt.append("2. O campo 'tipo' deve ser um dos seguintes: cliente, produto, fornecedor, pedido, geral\n");
        prompt.append("3. Extraia TODOS os dados mencionados no comando\n");
        prompt.append("4. Para campos não mencionados, NÃO invente valores\n");
        prompt.append("5. O campo 'retorno' deve conter uma mensagem amigável sobre a ação OU a resposta direta à pergunta\n");
        prompt.append("6. Mantenha exatamente os nomes dos campos mostrados nos exemplos\n");
        prompt.append("7. Para perguntas gerais (não relacionadas a cadastros), responda diretamente no campo 'retorno'\n\n");

        prompt.append("Agora processe o seguinte comando: ").append(userPrompt);

        return prompt.toString();
    }

    private String callOpenAI(String prompt) throws Exception {
        // Criar OpenAiService dinâmico com configurações atuais
        OpenAiService openAiService = openAiConfigService.createOpenAiService();

        if (openAiService == null) {
            log.warn("Serviço OpenAI não está configurado. Retornando resposta padrão.");
            return "{\"tipo\":\"erro\",\"retorno\":\"Serviço de IA não disponível ou desabilitado\"}";
        }

        // Buscar configurações dinâmicas
        var config = openAiConfigService.getConfig();

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(config.getModel())
                .messages(Arrays.asList(
                        new ChatMessage("system", "Você é um assistente que sempre responde apenas com JSON válido."),
                        new ChatMessage("user", prompt)
                ))
                .maxTokens(config.getMaxTokens())
                .temperature(0.3)
                .build();

        try {
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent().trim();
            log.debug("Resposta da OpenAI: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Erro ao chamar OpenAI: ", e);
            throw new Exception("Erro ao comunicar com OpenAI: " + e.getMessage());
        }
    }

    private AIProcessResponse parseAIResponse(String aiResponse) {
        // Tentar múltiplas estratégias de parsing

        // 1. Tentar parse direto
        try {
            return parseDirectJson(aiResponse);
        } catch (Exception e) {
            log.debug("Parse direto falhou, tentando outras estratégias");
        }

        // 2. Tentar remover blocos de código
        try {
            String cleaned = removeCodeBlocks(aiResponse);
            return parseDirectJson(cleaned);
        } catch (Exception e) {
            log.debug("Parse após limpeza falhou");
        }

        // 3. Tentar extrair JSON com regex
        try {
            String extracted = extractJsonWithRegex(aiResponse);
            return parseDirectJson(extracted);
        } catch (Exception e) {
            log.debug("Extração com regex falhou");
        }

        // Se tudo falhar, retornar como resposta genérica
        log.warn("Não foi possível parsear como JSON, retornando resposta genérica");
        return AIProcessResponse.builder()
                .tipo("geral")
                .retorno(aiResponse)
                .mensagem(aiResponse)
                .sucesso(true)
                .build();
    }

    private AIProcessResponse parseDirectJson(String json) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(json);

        AIProcessResponse response = new AIProcessResponse();
        response.setTipo(jsonNode.path("tipo").asText("geral"));
        response.setRetorno(jsonNode.path("retorno").asText(""));
        response.setMensagem(response.getRetorno());
        response.setSucesso(true);

        // Mapear todos os campos para um mapa de dados
        Map<String, Object> dados = new HashMap<>();
        jsonNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!key.equals("tipo") && !key.equals("retorno")) {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    dados.put(key, value.asText());
                } else if (value.isNumber()) {
                    dados.put(key, value.asDouble());
                } else if (value.isBoolean()) {
                    dados.put(key, value.asBoolean());
                } else {
                    dados.put(key, value.toString());
                }
            }
        });

        if (!dados.isEmpty()) {
            response.setDados(dados);
        }

        return response;
    }

    private String removeCodeBlocks(String text) {
        // Remove blocos ```json``` ou ```
        return text.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
    }

    private String extractJsonWithRegex(String text) {
        // Tentar encontrar JSON no texto
        Pattern pattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        throw new RuntimeException("Nenhum JSON encontrado no texto");
    }

    private void addTypeSpecificFieldsToJson(ObjectNode json, AIProcessResponse response) {
        if (response.getDados() == null) return;

        response.getDados().forEach((key, value) -> {
            if (value instanceof String) {
                json.put(key, (String) value);
            } else if (value instanceof Integer) {
                json.put(key, (Integer) value);
            } else if (value instanceof Double) {
                json.put(key, (Double) value);
            } else if (value instanceof Boolean) {
                json.put(key, (Boolean) value);
            } else if (value != null) {
                json.put(key, value.toString());
            }
        });
    }

    private AIProcessResponse createErrorResponse(String errorMessage) {
        return AIProcessResponse.builder()
                .tipo("erro")
                .retorno(errorMessage)
                .mensagem(errorMessage)
                .erro(errorMessage)
                .sucesso(false)
                .build();
    }

    public Map<String, List<String>> getExemplos(String tipo) {
        Map<String, List<String>> exemplos = new HashMap<>();

        if (tipo == null || tipo.equals("todos")) {
            exemplos.put("cliente", Arrays.asList(
                    "cadastrar cliente João Silva cpf 12345678900",
                    "novo cliente empresa ABC Ltda cnpj 12345678000190"
            ));
            exemplos.put("produto", Arrays.asList(
                    "criar produto Pão Francês preço 0.50 categoria pães",
                    "adicionar produto Bolo de Chocolate preço 35.00"
            ));
            exemplos.put("fornecedor", Arrays.asList(
                    "cadastrar fornecedor Distribuidora Pães Ltda cnpj 98765432000100",
                    "adicionar fornecedor Moinho Santa Rita cnpj 12345678000190 contato Maria"
            ));
        } else {
            exemplos.put(tipo, getExemplosPorTipo(tipo));
        }

        return exemplos;
    }

    private List<String> getExemplosPorTipo(String tipo) {
        switch (tipo) {
            case "cliente":
                return Arrays.asList(
                        "cadastrar cliente João Silva cpf 12345678900 email joao@email.com",
                        "novo cliente empresa ABC Ltda cnpj 12345678000190 telefone 1140001234"
                );
            case "produto":
                return Arrays.asList(
                        "criar produto Pão Francês preço 0.50 categoria pães",
                        "adicionar produto Bolo de Chocolate preço 35.00 tipo bolos"
                );
            case "fornecedor":
                return Arrays.asList(
                        "cadastrar fornecedor Distribuidora Pães Ltda cnpj 98765432000100",
                        "adicionar fornecedor Moinho Santa Rita cnpj 12345678000190 contato Maria telefone 1140001234",
                        "novo fornecedor Fábrica de Farinha São João email contato@farinha.com"
                );
            default:
                return Arrays.asList("Nenhum exemplo disponível para este tipo");
        }
    }

    private String getExemplosFormatados() {
        return """
        EXEMPLOS DE COMANDOS E RESPOSTAS ESPERADAS:

        1. CLIENTE:
        Comando: "cadastrar cliente João Silva cpf 12345678900 email joao@email.com telefone 11999887766"
        Resposta: {"tipo":"cliente","nome":"João Silva","cpf":"12345678900","email":"joao@email.com","telefone":"11999887766","tipo_pessoa":"Física","retorno":"Cliente João Silva será cadastrado"}

        2. PRODUTO:
        Comando: "criar produto Pão Francês preço 0.50 categoria pães"
        Resposta: {"tipo":"produto","nome":"Pão Francês","preco":0.50,"categoria":"pães","retorno":"Produto Pão Francês será cadastrado"}

        3. FORNECEDOR:
        Comando: "cadastrar fornecedor Distribuidora Pães Ltda cnpj 98765432000100 contato João telefone 1140001234"
        Resposta: {"tipo":"fornecedor","razao_social":"Distribuidora Pães Ltda","cnpj":"98765432000100","contato":"João","telefone":"1140001234","retorno":"Fornecedor Distribuidora Pães Ltda será cadastrado"}

        4. FORNECEDOR:
        Comando: "cadastrar fornecedor Moinho Santa Rita cnpj 12345678000190 email contato@moinho.com"
        Resposta: {"tipo":"fornecedor","razao_social":"Moinho Santa Rita","cnpj":"12345678000190","email":"contato@moinho.com","retorno":"Fornecedor Moinho Santa Rita será cadastrado"}

        5. PERGUNTA GERAL:
        Comando: "qual a capital da frança?"
        Resposta: {"tipo":"geral","retorno":"A capital da França é Paris."}

        6. PERGUNTA GERAL:
        Comando: "como fazer pão?"
        Resposta: {"tipo":"geral","retorno":"Para fazer pão: misture farinha, água, sal e fermento. Amasse bem, deixe descansar até dobrar de tamanho e asse em forno pré-aquecido a 200°C por 30-40 minutos."}
        """;
    }
}

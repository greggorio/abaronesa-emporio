package com.baronesa.emporio.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentFriendlyMessageProvider {

    private static final Map<String, String> FRIENDLY_MESSAGES;

    static {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("installments_excludes_country", "Esse cartão não permite parcelamento. Tente pagar à vista ou com outro cartão.");
        entries.put("card_declined", "O cartão foi recusado. Tente outro cartão ou atualize os dados.");
        entries.put("encrypted card already used", "Este cartão já foi utilizado. Gere um novo token e tente novamente.");
        entries.put("invalid_parameter", "Dados do cartão inválidos. Gere um novo token antes de efetuar o pagamento.");
        entries.put("invalid_base64", "Dados do cartão corrompidos. Atualize e tente novamente.");
        entries.put("invalid_value", "Algum campo do cartão está incorreto. Verifique e tente novamente.");
        FRIENDLY_MESSAGES = Collections.unmodifiableMap(entries);
    }

    private final ObjectMapper objectMapper;

    public PaymentFriendlyMessageProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String resolve(String detail, String rawPayload) {
        String normalized = (detail != null ? detail : "");
        String lower = normalized.toLowerCase();
        for (Map.Entry<String, String> entry : FRIENDLY_MESSAGES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (rawPayload != null) {
            try {
                JsonNode root = objectMapper.readTree(rawPayload);
                if (root.has("cause") && root.get("cause").isArray()) {
                    for (JsonNode cause : root.get("cause")) {
                        String code = textOrNull(cause, "code");
                        String description = textOrNull(cause, "description");
                        for (Map.Entry<String, String> entry : FRIENDLY_MESSAGES.entrySet()) {
                            if (code != null && code.equalsIgnoreCase(entry.getKey())) {
                                return entry.getValue();
                            }
                            if (description != null && description.toLowerCase().contains(entry.getKey())) {
                                return entry.getValue();
                            }
                        }
                        if (description != null) {
                            return description;
                        }
                    }
                }
                if (root.has("message")) {
                    return root.get("message").asText();
                }
                if (root.has("status_detail")) {
                    return root.get("status_detail").asText();
                }
            } catch (Exception ignored) {
                // fallback to raw detail
            }
        }
        return detail;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}

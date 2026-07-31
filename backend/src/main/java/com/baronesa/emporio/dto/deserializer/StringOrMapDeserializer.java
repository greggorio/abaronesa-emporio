package com.baronesa.emporio.dto.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.Map;

public class StringOrMapDeserializer extends JsonDeserializer<Map<String, String>> {
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public Map<String, String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == null) {
            token = parser.nextToken();
        }

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        if (token == JsonToken.VALUE_STRING) {
            String raw = parser.getValueAsString();
            if (raw == null) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return null;
            }

            ObjectMapper mapper = parser.getCodec() instanceof ObjectMapper
                    ? (ObjectMapper) parser.getCodec()
                    : new ObjectMapper();
            ObjectReader reader = mapper.readerFor(TYPE);
            try {
                return reader.readValue(trimmed);
            } catch (JsonProcessingException e) {
                throw JsonMappingException.from(parser, "Formato inválido para phrases (esperado JSON válido).", e);
            }
        }

        ObjectMapper mapper = parser.getCodec() instanceof ObjectMapper
                ? (ObjectMapper) parser.getCodec()
                : new ObjectMapper();
        return mapper.readValue(parser, TYPE);
    }
}

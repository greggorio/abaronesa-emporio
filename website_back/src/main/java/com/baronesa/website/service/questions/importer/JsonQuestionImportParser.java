package com.baronesa.website.service.questions.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JsonQuestionImportParser implements ImportParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(String filename, String contentType) {
        if (contentType != null && contentType.contains("json")) {
            return true;
        }
        if (filename != null && filename.toLowerCase().endsWith(".json")) {
            return true;
        }
        return false;
    }

    @Override
    public List<QuestionImportRow> parse(InputStream inputStream) throws Exception {
        log.info("Starting JSON parsing");

        // Read the entire input stream as string
        String jsonString = new java.util.Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        log.info("JSON content length: {} chars", jsonString.length());
        log.debug("JSON content preview (first 500 chars): {}", jsonString.substring(0, Math.min(500, jsonString.length())));

        // Parse the JSON array
        TypeReference<List<QuestionImportRow>> typeRef = new TypeReference<List<QuestionImportRow>>() {};
        List<QuestionImportRow> rows = objectMapper.readValue(jsonString, typeRef);

        log.info("Parsed {} rows from JSON", rows.size());

        // Set indices since they might not be in the JSON
        for (int i = 0; i < rows.size(); i++) {
            QuestionImportRow row = rows.get(i);
            if (row.getIndex() == null) {
                row.setIndex(i + 1);
            }
        }

        return rows;
    }

    @Override
    public String getFormatName() {
        return "JSON";
    }
}
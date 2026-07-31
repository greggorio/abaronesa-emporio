package com.baronesa.website.service.questions.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvQuestionImportParser implements ImportParser {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String filename, String contentType) {
        if (contentType != null && contentType.contains("csv")) {
            return true;
        }
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return true;
        }
        return false;
    }

    @Override
    public List<QuestionImportRow> parse(InputStream inputStream) throws Exception {
        List<QuestionImportRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() >= 7) {
                    String question = fields.get(0);
                    String optionsStr = fields.get(1);
                    String correctAnswerStr = fields.get(2);
                    String pointsStr = fields.get(3);
                    String activeStr = fields.get(4);
                    String categoryIdStr = fields.get(5);
                    String imageUrl = fields.get(6);

                    List<String> options = parseOptionsFromJson(optionsStr);
                    Integer correctAnswer = parseInteger(correctAnswerStr);
                    Integer points = parseInteger(pointsStr, 10);
                    Boolean active = parseActive(activeStr);
                    Long categoryId = parseLong(categoryIdStr);

                    QuestionImportRow row = new QuestionImportRow(
                            rows.size() + 1,
                            question,
                            options,
                            correctAnswer,
                            points,
                            active,
                            categoryId,
                            imageUrl
                    );

                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    private List<String> parseOptionsFromJson(String optionsStr) {
        if (optionsStr == null || optionsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(optionsStr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Não foi possível desserializar o campo options ({}) via JSON: {}", optionsStr, e.getMessage());
        }

        String normalized = optionsStr.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> options = new ArrayList<>();
        for (String option : normalized.split(",")) {
            options.add(option.trim().replaceAll("^\"|\"$", "").trim());
        }
        return options;
    }

    private Integer parseInteger(String value) {
        return parseInteger(value, null);
    }

    private Integer parseInteger(String value, Integer fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseActive(String activeStr) {
        if (activeStr == null || activeStr.isEmpty()) {
            return true;
        }

        String lowerActive = activeStr.toLowerCase().trim();
        return lowerActive.equals("true") || lowerActive.equals("1") || lowerActive.equals("yes");
    }

    @Override
    public String getFormatName() {
        return "CSV";
    }
}

package com.baronesa.website.service.questions.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportParserRegistry {

    private final CsvQuestionImportParser csvParser;
    private final JsonQuestionImportParser jsonParser;

    public ImportParser getParser(String filename, String contentType, InputStream inputStream) {
        log.info("getParser called - filename: {}, contentType: {}", filename, contentType);

        // Try to determine the parser based on content type first
        if (contentType != null) {
            if (contentType.contains("csv")) {
                log.info("Selected CSV parser based on content type");
                return csvParser;
            } else if (contentType.contains("json")) {
                log.info("Selected JSON parser based on content type");
                return jsonParser;
            }
        }

        // Fallback to filename extension
        if (filename != null) {
            if (filename.toLowerCase().endsWith(".csv")) {
                log.info("Selected CSV parser based on filename");
                return csvParser;
            } else if (filename.toLowerCase().endsWith(".json")) {
                log.info("Selected JSON parser based on filename");
                return jsonParser;
            }
        }

        log.error("Unsupported file format - filename: {}, contentType: {}", filename, contentType);
        throw new IllegalArgumentException("Unsupported file format");
    }
}
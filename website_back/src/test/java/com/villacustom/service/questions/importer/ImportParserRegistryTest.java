package com.baronesa.website.service.questions.importer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ImportParserRegistryTest {

    @Mock
    private CsvQuestionImportParser csvParser;

    @Mock
    private JsonQuestionImportParser jsonParser;

    private ImportParserRegistry registry;

    private final InputStream dummyStream = new ByteArrayInputStream("dummy".getBytes());

    @BeforeEach
    void setUp() {
        registry = new ImportParserRegistry(csvParser, jsonParser);
    }

    @Test
    void shouldReturnCsvParserForCsvContentType() {
        ImportParser result = registry.getParser("file.txt", "text/csv", dummyStream);
        assertThat(result).isSameAs(csvParser);
    }

    @Test
    void shouldReturnJsonParserForJsonContentType() {
        ImportParser result = registry.getParser("file.txt", "application/json", dummyStream);
        assertThat(result).isSameAs(jsonParser);
    }

    @Test
    void shouldReturnCsvParserForCsvFilename() {
        ImportParser result = registry.getParser("data.csv", null, dummyStream);
        assertThat(result).isSameAs(csvParser);
    }

    @Test
    void shouldReturnJsonParserForJsonFilename() {
        ImportParser result = registry.getParser("data.json", null, dummyStream);
        assertThat(result).isSameAs(jsonParser);
    }

    @Test
    void shouldThrowForUnknownFormat() {
        assertThatThrownBy(() ->
                registry.getParser("data.txt", "text/plain", dummyStream)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void shouldThrowWhenBothNull() {
        assertThatThrownBy(() ->
                registry.getParser(null, null, dummyStream)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void shouldPreferContentTypeOverFilename() {
        ImportParser result = registry.getParser("data.json", "text/csv", dummyStream);
        assertThat(result).isSameAs(csvParser);
    }
}

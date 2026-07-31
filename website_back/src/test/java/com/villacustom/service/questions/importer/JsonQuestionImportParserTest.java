package com.baronesa.website.service.questions.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonQuestionImportParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonQuestionImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonQuestionImportParser();
        ReflectionTestUtils.setField(parser, "objectMapper", objectMapper);
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportJsonContentType() {
            assertThat(parser.supports("file.txt", "application/json")).isTrue();
            assertThat(parser.supports("file.txt", "text/json")).isTrue();
        }

        @Test
        void shouldSupportJsonExtension() {
            assertThat(parser.supports("data.json", null)).isTrue();
            assertThat(parser.supports("data.JSON", "text/plain")).isTrue();
        }

        @Test
        void shouldNotSupportOtherFormats() {
            assertThat(parser.supports("data.csv", "text/csv")).isFalse();
            assertThat(parser.supports("data.txt", null)).isFalse();
        }
    }

    @Nested
    class Parse {

        @Test
        void shouldParseValidJsonArray() throws Exception {
            String json = """
                    [
                        {
                            "question": "Qual a capital do Brasil?",
                            "options": ["São Paulo", "Brasília", "Rio de Janeiro"],
                            "correctAnswer": 1,
                            "points": 10,
                            "active": true,
                            "categoryId": 100
                        },
                        {
                            "question": "Quanto é 2+2?",
                            "options": ["3", "4", "5"],
                            "correctAnswer": 1,
                            "points": 5,
                            "active": false,
                            "categoryId": 101
                        }
                    ]
                    """;

            InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            List<QuestionImportRow> rows = parser.parse(input);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getQuestion()).isEqualTo("Qual a capital do Brasil?");
            assertThat(rows.get(0).getOptions()).containsExactly("São Paulo", "Brasília", "Rio de Janeiro");
            assertThat(rows.get(0).getCorrectAnswer()).isEqualTo(1);
            assertThat(rows.get(0).getPoints()).isEqualTo(10);
            assertThat(rows.get(0).getActive()).isTrue();
            assertThat(rows.get(0).getCategoryId()).isEqualTo(100L);
            assertThat(rows.get(0).getIndex()).isEqualTo(1);

            assertThat(rows.get(1).getQuestion()).isEqualTo("Quanto é 2+2?");
            assertThat(rows.get(1).getPoints()).isEqualTo(5);
            assertThat(rows.get(1).getActive()).isFalse();
            assertThat(rows.get(1).getCategoryId()).isEqualTo(101L);
            assertThat(rows.get(1).getIndex()).isEqualTo(2);
        }

        @Test
        void shouldSetIndexWhenNotProvided() throws Exception {
            String json = """
                    [
                        {"question": "Q1?", "options": ["A","B"], "correctAnswer": 0, "categoryId": 1},
                        {"question": "Q2?", "options": ["C","D"], "correctAnswer": 0, "categoryId": 1}
                    ]
                    """;

            InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            List<QuestionImportRow> rows = parser.parse(input);

            assertThat(rows.get(0).getIndex()).isEqualTo(1);
            assertThat(rows.get(1).getIndex()).isEqualTo(2);
        }

        @Test
        void shouldPreserveIndexWhenProvided() throws Exception {
            String json = """
                    [
                        {"index": 10, "question": "Q1?", "options": ["A","B"], "correctAnswer": 0, "categoryId": 1}
                    ]
                    """;

            InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            List<QuestionImportRow> rows = parser.parse(input);

            assertThat(rows.get(0).getIndex()).isEqualTo(10);
        }

        @Test
        void shouldReturnEmptyListForEmptyArray() throws Exception {
            String json = "[]";
            InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            List<QuestionImportRow> rows = parser.parse(input);
            assertThat(rows).isEmpty();
        }
    }

    @Nested
    class GetFormatName {

        @Test
        void shouldReturnJson() {
            assertThat(parser.getFormatName()).isEqualTo("JSON");
        }
    }
}

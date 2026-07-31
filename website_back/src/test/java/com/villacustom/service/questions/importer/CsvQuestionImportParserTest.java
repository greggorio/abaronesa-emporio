package com.baronesa.website.service.questions.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvQuestionImportParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CsvQuestionImportParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvQuestionImportParser(objectMapper);
    }

    @Test
    void shouldSupportCsvContentType() {
        assertThat(parser.supports("file.txt", "text/csv")).isTrue();
        assertThat(parser.supports("file.txt", "application/csv")).isTrue();
    }

    @Test
    void shouldSupportCsvExtension() {
        assertThat(parser.supports("data.csv", null)).isTrue();
        assertThat(parser.supports("data.CSV", "text/plain")).isTrue();
    }

    @Test
    void shouldNotSupportOtherFormats() {
        assertThat(parser.supports("data.json", "application/json")).isFalse();
        assertThat(parser.supports("data.txt", null)).isFalse();
    }

    @Test
    void shouldParseValidCsv() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "Qual a capital do Brasil?,\"[\"\"SP\"\",\"\"Brasília\"\",\"\"RJ\"\"]\",1,10,true,100,\n"
                + "Quanto é 2+2?,\"[\"\"3\"\",\"\"4\"\",\"\"5\"\"]\",1,5,true,101,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getQuestion()).isEqualTo("Qual a capital do Brasil?");
        assertThat(rows.get(0).getCorrectAnswer()).isEqualTo(1);
        assertThat(rows.get(0).getPoints()).isEqualTo(10);
        assertThat(rows.get(0).getActive()).isTrue();
        assertThat(rows.get(0).getCategoryId()).isEqualTo(100L);

        assertThat(rows.get(1).getQuestion()).isEqualTo("Quanto é 2+2?");
        assertThat(rows.get(1).getCorrectAnswer()).isEqualTo(1);
        assertThat(rows.get(1).getPoints()).isEqualTo(5);
        assertThat(rows.get(1).getCategoryId()).isEqualTo(101L);
    }

    @Test
    void shouldParseOptionsFromQuotedCsvField() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "Pergunta 1?,\"[\"\"A\"\",\"\"B\"\",\"\"C\"\"]\",0,10,true,100,\n"
                + "Pergunta 2?,\"[\"\"D\"\",\"\"E\"\",\"\"F\"\"]\",1,5,false,101,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getOptions()).containsExactly("A", "B", "C");
        assertThat(rows.get(1).getOptions()).containsExactly("D", "E", "F");
    }

    @Test
    void shouldSkipHeaderAndEmptyLines() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "\n"
                + "Pergunta 1?,\"[\"\"A\"\",\"\"B\"\"]\",0,10,true,100,\n"
                + "   \n"
                + "Pergunta 2?,\"[\"\"C\"\",\"\"D\"\"]\",1,5,false,101,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(2);
    }

    @Test
    void shouldHandleQuotedFields() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "\"Pergunta com , vírgula\",\"[\"\"A\"\",\"\"B\"\"]\",0,10,true,100,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getQuestion()).isEqualTo("Pergunta com , vírgula");
    }

    @Test
    void shouldUseDefaultsWhenFieldsAreEmpty() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "Pergunta?,\"[\"\"A\"\",\"\"B\"\"]\",0,,,100,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPoints()).isEqualTo(10);
        assertThat(rows.get(0).getActive()).isTrue();
    }

    @Test
    void shouldReturnEmptyListForOnlyHeader() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n";
        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);
        assertThat(rows).isEmpty();
    }

    @Test
    void shouldSkipRowsWithFewerThanSevenFields() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "incomplete,\"[\"\"A\"\"]\",0\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).isEmpty();
    }

    @Test
    void shouldParseNonJsonOptionsUsingFallback() throws Exception {
        String csv = "question,options,correctAnswer,points,active,categoryId,imageUrl\n"
                + "Pergunta?,\"[A,B,C]\",0,10,true,100,\n";

        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<QuestionImportRow> rows = parser.parse(input);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOptions()).containsExactly("A", "B", "C");
    }

    @Test
    void shouldReturnCsvFormatName() {
        assertThat(parser.getFormatName()).isEqualTo("CSV");
    }
}

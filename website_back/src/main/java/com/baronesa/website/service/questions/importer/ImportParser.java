package com.baronesa.website.service.questions.importer;

import java.io.InputStream;
import java.util.List;

public interface ImportParser {
    boolean supports(String filename, String contentType);
    List<QuestionImportRow> parse(InputStream inputStream) throws Exception;
    String getFormatName();
}
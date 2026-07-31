package com.baronesa.website.service.questions.importer;

import com.baronesa.website.dto.questions.QuestionImportRequestDTO;
import com.baronesa.website.dto.questions.QuestionImportResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class QuestionImportAppService {

    private final QuestionImportService questionImportService;

    public QuestionImportResultDTO importQuestions(
            String filename,
            String contentType,
            InputStream inputStream,
            QuestionImportRequestDTO request) throws Exception {

        if (request == null) {
            request = new QuestionImportRequestDTO();
        }

        return questionImportService.importQuestions(filename, contentType, inputStream, request);
    }
}

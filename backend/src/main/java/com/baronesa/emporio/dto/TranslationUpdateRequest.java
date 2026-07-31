package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.TranslationStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslationUpdateRequest {
    private String translatedText;
    private TranslationStatus status;
}

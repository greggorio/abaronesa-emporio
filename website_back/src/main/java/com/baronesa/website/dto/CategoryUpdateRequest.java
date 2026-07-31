package com.baronesa.website.dto;

import com.baronesa.website.enums.DifficultyLevel;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpdateRequest {

    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    private String name;

    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    private String description;

    private DifficultyLevel difficultyLevel;

    @Size(max = 100, message = "Ícone deve ter no máximo 100 caracteres")
    private String icon;

    @Size(min = 7, max = 7, message = "Cor deve estar no formato hexadecimal (#RRGGBB)")
    private String color;

    private Boolean active;
}


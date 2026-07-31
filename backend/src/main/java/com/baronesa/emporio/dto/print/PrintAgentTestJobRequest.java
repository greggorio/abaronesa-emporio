package com.baronesa.emporio.dto.print;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintAgentTestJobRequest {

    @NotBlank(message = "Route é obrigatório")
    private String route;

    private String text;
}

package com.baronesa.emporio.dto.print;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrintAgentPairingRequest {

    @JsonProperty("pairing_code")
    @NotBlank(message = "Código de pareamento é obrigatório")
    private String pairingCode;
}

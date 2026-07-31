package com.baronesa.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlayerJoinRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String nickname;

    @NotBlank
    private String sessionCode;
}

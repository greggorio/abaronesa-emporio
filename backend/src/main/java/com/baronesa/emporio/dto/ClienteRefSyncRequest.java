package com.baronesa.emporio.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClienteRefSyncRequest(
    Long id,
    String nome,
    String email,
    String telefone,
    String cpf,
    LocalDate dataNascimento,
    Boolean ativo,
    LocalDateTime erpUpdatedAt
) {
}
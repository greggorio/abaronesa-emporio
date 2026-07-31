package com.baronesa.emporio.dto;

import java.util.List;

public record RegistrarPagamentosMultiplosRequest(
        List<RegistrarPagamentoRequest> pagamentos
) {}

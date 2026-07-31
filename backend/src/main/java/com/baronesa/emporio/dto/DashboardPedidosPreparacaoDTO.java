package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPedidosPreparacaoDTO {
    private Long pedidosNoBar;
    private Long aguardandoPreparoBar;
    private Long pedidosNaCozinha;
    private Long emPreparoCozinha;
    private Long pedidosEmEspera;
    private Long aguardandoEntrega;
}

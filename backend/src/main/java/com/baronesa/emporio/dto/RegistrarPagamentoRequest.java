package com.baronesa.emporio.dto;

public record RegistrarPagamentoRequest(
        Long sessaoConvidadoId,  // Beneficiário (null = mesa toda)
        Long paganteId,          // Quem está pagando (null = indefinido)
        Long valorCentavos,
        String metodo,           // pix | card | cash
        String cartaoTipo,       // credito | debito (opcional, apenas para card)
        java.util.List<RegistrarPagamentoAlocacaoRequest> alocacoes, // opcional: alocar pagamento de mesa entre convidados
        Long valorTaxaServicoCentavos, // opcional: valor em centavos da taxa de serviço, se informado
        Boolean incluiTaxaServico,     // indica se este pagamento inclui taxa de serviço
        Long valorCouvertCentavos      // opcional: valor em centavos do couvert artístico
) {}

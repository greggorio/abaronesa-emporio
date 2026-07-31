package com.baronesa.emporio.dto;

import java.util.List;

public record ContaMesaResponse(
        long totalMesaCentavos,          // subtotal + taxa sugerida - descontos (compat)
        long taxaServicoCentavos,        // taxa sugerida (compat)
        long descontosCentavos,
        long pagoCentavos,               // total pago (base + taxa) (compat)
        long devidoCentavos,             // devido de consumo (base) — fechamento usa este
        List<Pessoa> pessoas,
        long subtotalCentavos,           // subtotal de consumo (sem taxa)
        long pagoBaseCentavos,           // pago de consumo/base
        long pagoTaxaServicoCentavos,    // pago de taxa de serviço
        long taxaServicoPagaCentavos,    // alias pagoTaxaServicoCentavos
        long taxaServicoPendenteCentavos, // taxa sugerida - taxa paga
        long couvertCentavos,            // total de couvert lançado
        long pagoCouvertCentavos,        // pago de couvert
        long devidoCouvertCentavos,      // devido de couvert
        long devidoTotalCentavos,        // devido base + taxa pendente + couvert pendente
        boolean selfCheckoutLiberado     // liberação do garçom para self-checkout
) {
    public record Pessoa(
            Long sessaoConvidadoId,
            String nome,
            long subtotalCentavos,
            long ajustesCentavos,
            long pagoCentavos,                 // total pago (base + taxa) para compatibilidade
            long devidoCentavos,               // devido de consumo (base)
            long pagoBaseCentavos,
            long pagoTaxaServicoCentavos,
            long taxaServicoSugeridaCentavos,
            long taxaServicoPendenteCentavos,
            long couvertCentavos,              // couvert por pessoa
            long pagoCouvertCentavos,          // pago de couvert por pessoa
            long devidoCouvertCentavos,        // devido de couvert por pessoa
            long devidoTotalCentavos           // devido total por pessoa (base + taxa + couvert)
    ) {}
}

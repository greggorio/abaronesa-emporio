package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ContaReceberParcelaRequest;
import com.baronesa.emporio.dto.ContaReceberRequest;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.repository.ContaReceberRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Job para gerar Contas a Receber de excedente de voucher consumido no mês anterior.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherExcedenteJobService {

    private static final String TIPO_RECEITA_NOME = "Excedente de voucher";
    private static final DateTimeFormatter DOC_YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PagamentoRepository pagamentoRepository;
    private final TipoReceitaRepository tipoReceitaRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberService contaReceberService;

    public Result executar() {
        YearMonth competenciaAnterior = YearMonth.now().minusMonths(1);
        LocalDate inicio = competenciaAnterior.atDay(1);
        LocalDate fim = competenciaAnterior.atEndOfMonth().plusDays(1); // exclusive

        // Buscar consumo de voucher no período
        List<Object[]> consumo = pagamentoRepository.findConsumoVoucherFuncionarios(
                inicio.atStartOfDay(),
                fim.atStartOfDay()
        );

        TipoReceita tipoReceita = tipoReceitaRepository.findByNomeIgnoreCase(TIPO_RECEITA_NOME)
                .orElseThrow(() -> new IllegalStateException("Tipo de receita não encontrado: " + TIPO_RECEITA_NOME));

        int criados = 0;
        StringBuilder logMsg = new StringBuilder();

        for (Object[] row : consumo) {
            Long usuarioId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String nome = (String) row[1];
            BigDecimal total = (BigDecimal) row[2];
            BigDecimal voucherVr = (BigDecimal) row[3];

            if (usuarioId == null || total == null || voucherVr == null) continue;

            BigDecimal excedente = total.subtract(voucherVr);
            if (excedente.compareTo(BigDecimal.ZERO) <= 0) continue;

            String competenciaStr = competenciaAnterior.format(DOC_YYYYMM);
            String numeroDocumento = "VOUCHER-EXC-%d-%s".formatted(usuarioId, competenciaStr);

            if (contaReceberRepository.existsByNumeroDocumento(numeroDocumento)) {
                log.debug("Conta já existe para usuario {} competencia {}", usuarioId, competenciaStr);
                continue;
            }

            LocalDate vencimento = ajustarParaProximoDiaUtil(YearMonth.now().atDay(5));

            var parcela = new ContaReceberParcelaRequest(
                    null,
                    1,
                    excedente,
                    vencimento,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    null
            );

            var contaReq = new ContaReceberRequest(
                    usuarioId,
                    tipoReceita.getId(),
                    numeroDocumento,
                    "Excedente voucher %s — %s".formatted(competenciaAnterior.format(MES_ANO), nome),
                    excedente,
                    1,
                    "Gerado automaticamente pelo job de excedente de voucher.",
                    false,
                    List.of(parcela)
            );

            try {
                contaReceberService.criar(contaReq);
                criados++;
                logMsg.append("CRIADA conta usuario=").append(usuarioId)
                        .append(" doc=").append(numeroDocumento)
                        .append(" valor=").append(excedente)
                        .append("; ");
            } catch (Exception e) {
                log.warn("Falha ao criar conta de excedente para usuario {}: {}", usuarioId, e.getMessage());
            }
        }

        return new Result(criados, logMsg.toString());
    }

    private LocalDate ajustarParaProximoDiaUtil(LocalDate data) {
        if (data.getDayOfWeek() == DayOfWeek.SATURDAY) return data.plusDays(2);
        if (data.getDayOfWeek() == DayOfWeek.SUNDAY) return data.plusDays(1);
        return data;
    }

    public record Result(int criados, String message) {}
}

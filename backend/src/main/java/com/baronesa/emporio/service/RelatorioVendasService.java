package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.relatorios.DadosEmpresaDTO;
import com.baronesa.emporio.dto.relatorios.PagamentoDetalheDTO;
import com.baronesa.emporio.dto.relatorios.RelatorioVendasDTO;
import com.baronesa.emporio.dto.relatorios.RelatorioVendasFiltroDTO;
import com.baronesa.emporio.dto.relatorios.ResumoFormaPagamentoDTO;
import com.baronesa.emporio.dto.relatorios.ResumoVendasDTO;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.security.SecurityUtils;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioVendasService {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");

    private final PagamentoRepository pagamentoRepository;
    private final TemplateEngine templateEngine;
    private final ConfigManager configManager;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioVendasPdf(RelatorioVendasFiltroDTO filtros) {
        log.info("Gerando relatório de vendas - dataInicio={} dataFim={}", filtros.dataInicio(), filtros.dataFim());

        RelatorioVendasDTO relatorio = montarRelatorio(filtros);

        Context context = new Context(LOCALE_BR);
        context.setVariable("relatorio", relatorio);
        context.setVariable("empresa", relatorio.empresa());
        context.setVariable("filtros", relatorio.filtros());
        context.setVariable("resumo", relatorio.resumo());
        context.setVariable("movimentos", relatorio.pagamentos());
        context.setVariable("pagamentos", relatorio.pagamentos());
        context.setVariable("resumoPorFormaPagamento", relatorio.resumoPorFormaPagamento());

        String logoBase64Config = normalizeLogoConfig(configManager.getConfig("nfe_logo_base64", ""));
        if (!logoBase64Config.isBlank()) {
            context.setVariable("logoBase64", logoBase64Config);
        } else {
        String logoPath = relatorio.empresa().logoPath();
        if (logoPath != null) {
            String trimmedLogoPath = logoPath.trim();
            if (!trimmedLogoPath.isEmpty()) {
                File logoFile = new File(trimmedLogoPath);
                if (logoFile.exists() && logoFile.isFile()) {
                    String logoBase64 = getLogoAsBase64(trimmedLogoPath);
                    if (logoBase64 != null) {
                        context.setVariable("logoBase64", logoBase64);
                    }
                }
            }
        }
        }

        String htmlContent = templateEngine.process("relatorio-vendas", context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);
            byte[] pdfBytes = outputStream.toByteArray();
            log.info("Relatório de vendas convertido em PDF com {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            log.error("Erro ao gerar PDF do relatório de vendas", e);
            throw new RuntimeException("Erro ao gerar relatório de vendas", e);
        }
    }

    private RelatorioVendasDTO montarRelatorio(RelatorioVendasFiltroDTO filtros) {
        LocalDateTime inicio = filtros.dataInicio().atStartOfDay();
        LocalDateTime fimInclusivo = filtros.dataFim().atTime(LocalTime.MAX);
        LocalDateTime fimQuery = filtros.dataFim().plusDays(1).atStartOfDay();

        List<Pagamento> pagamentos = pagamentoRepository.findByPagoEmBetween(inicio, fimInclusivo);
        pagamentos.sort(Comparator.comparing(Pagamento::getPagoEm, Comparator.nullsLast(Comparator.naturalOrder())));

        List<PagamentoDetalheDTO> pagamentosDetalhados = pagamentos.stream()
                .map(PagamentoDetalheDTO::from)
                .toList();

        BigDecimal faturamentoTotal = safe(pagamentoRepository.sumValorPagoByDate(inicio, fimQuery));
        BigDecimal valorBase = safe(pagamentoRepository.sumValorBaseByDate(inicio, fimQuery));
        BigDecimal taxaServico = safe(pagamentoRepository.sumValorTaxaServicoByDate(inicio, fimQuery));

        List<ResumoFormaPagamentoDTO> resumoPorFormaPagamento = pagamentoRepository
                .findValoresPorMetodoByDate(inicio, fimQuery)
                .stream()
                .map(this::mapResumoPorFormaPagamento)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResumoFormaPagamentoDTO::valor).reversed())
                .toList();

        List<Pagamento> pagamentosComCouvert = pagamentoRepository.findByPagoEmBetweenAndValorCouvertIsNotNull(inicio, fimInclusivo);
        BigDecimal valorCouvert = pagamentosComCouvert.stream()
                .map(Pagamento::getValorCouvert)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pagamentosComCouvertCount = pagamentosComCouvert.stream().count();

        long numeroVendas = pagamentosDetalhados.size();
        BigDecimal ticketMedio = numeroVendas > 0
                ? faturamentoTotal.divide(BigDecimal.valueOf(numeroVendas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        ResumoVendasDTO resumo = new ResumoVendasDTO(
                faturamentoTotal,
                valorBase,
                taxaServico,
                valorCouvert,
                pagamentosComCouvertCount,
                numeroVendas,
                ticketMedio
        );

        DadosEmpresaDTO empresa = buscarDadosEmpresa();

        String usuarioGeracao;
        try {
            usuarioGeracao = securityUtils.getUsuarioAtualNome();
        } catch (Exception e) {
            log.warn("Não foi possível obter o usuário atual para o relatório de vendas: {}", e.getMessage());
            usuarioGeracao = "Sistema";
        }

        return new RelatorioVendasDTO(
                empresa,
                filtros,
                resumo,
                pagamentosDetalhados,
                resumoPorFormaPagamento,
                LocalDateTime.now(),
                usuarioGeracao
        );
    }

    private ResumoFormaPagamentoDTO mapResumoPorFormaPagamento(Object[] row) {
        if (row == null || row.length < 3) {
            return null;
        }

        String metodo = row[0] != null ? row[0].toString() : null;
        String cartaoTipo = row[1] != null ? row[1].toString() : null;
        BigDecimal total = toBigDecimal(row[2]);

        return new ResumoFormaPagamentoDTO(metodo, cartaoTipo, total);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("Valor de resumo por forma de pagamento inválido: {}", str);
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private DadosEmpresaDTO buscarDadosEmpresa() {
        return new DadosEmpresaDTO(
                configManager.getConfig("nfe_razao_social", ""),
                configManager.getConfig("nfe_nome_fantasia", ""),
                configManager.getConfig("nfe_cnpj", ""),
                configManager.getConfig("nfe_inscricao_estadual", ""),
                configManager.getConfig("nfe_logradouro", ""),
                configManager.getConfig("nfe_numero", ""),
                configManager.getConfig("nfe_bairro", ""),
                configManager.getConfig("nfe_municipio", ""),
                configManager.getConfig("nfe_uf", ""),
                configManager.getConfig("nfe_cep", ""),
                configManager.getConfig("nfe_telefone", ""),
                configManager.getConfig("nfe_logo_path", "")
        );
    }

    private String normalizeLogoConfig(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        String sanitized = trimmed.replaceAll("\\s+", "");
        return "data:image/png;base64," + sanitized;
    }

    private String getLogoAsBase64(String logoPath) {
        try {
            File logoFile = new File(logoPath);
            byte[] content = Files.readAllBytes(logoFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(content);

            String mimeType = "image/png";
            String lower = logoPath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }

            return "data:" + mimeType + ";base64," + base64;
        } catch (Exception e) {
            log.warn("Não foi possível carregar o logo para o relatório de vendas: {}", e.getMessage());
            return null;
        }
    }
}

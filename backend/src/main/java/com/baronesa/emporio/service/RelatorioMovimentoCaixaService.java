package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.relatorios.*;
import com.baronesa.emporio.entity.MovimentoCaixa;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.repository.MovimentoCaixaRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioMovimentoCaixaService {

    private final MovimentoCaixaRepository movimentoCaixaRepository;
    private final TemplateEngine templateEngine;
    private final ConfigManager configManager;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioMovimentoCaixaPdf(MovimentoCaixaFiltroDTO filtros) {
        try {
            log.info("Gerando relatório de movimento de caixa - Data: {}", filtros.data());

            RelatorioMovimentoCaixaDTO relatorio = gerarDadosRelatorio(filtros);

            Context context = new Context(new Locale("pt", "BR"));
            context.setVariable("relatorio", relatorio);
            context.setVariable("empresa", relatorio.empresa());
            context.setVariable("filtros", relatorio.filtros());
            context.setVariable("resumo", relatorio.resumo());
            context.setVariable("movimentos", relatorio.movimentos());
            context.setVariable("resumoPorFormaPagamento", relatorio.resumoPorFormaPagamento());
            context.setVariable("resumoPorTipo", relatorio.resumoPorTipo());

            String logoBase64Config = normalizeLogoConfig(configManager.getConfig("nfe_logo_base64", ""));
            if (!logoBase64Config.isBlank()) {
                context.setVariable("logoBase64", logoBase64Config);
            } else {
                String logoPath = relatorio.empresa().logoPath();
                if (logoPath != null && new File(logoPath).exists()) {
                    String logoBase64 = getLogoAsBase64(logoPath);
                    context.setVariable("logoBase64", logoBase64);
                }
            }

            String htmlContent = templateEngine.process("relatorio-movimento-caixa", context);

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ITextRenderer renderer = new ITextRenderer();
                renderer.setDocumentFromString(htmlContent);
                renderer.layout();
                renderer.createPDF(outputStream);
                byte[] pdfBytes = outputStream.toByteArray();
                log.info("PDF de movimento de caixa gerado com sucesso. Tamanho: {} bytes", pdfBytes.length);
                return pdfBytes;
            }
        } catch (Exception e) {
            log.error("Erro ao gerar PDF do relatório de movimento de caixa", e);
            throw new RuntimeException("Erro ao gerar relatório: " + e.getMessage(), e);
        }
    }

    private RelatorioMovimentoCaixaDTO gerarDadosRelatorio(MovimentoCaixaFiltroDTO filtros) {
        DadosEmpresaDTO empresa = buscarDadosEmpresa();
        BigDecimal saldoInicial = calcularSaldoInicial(filtros.data());
        List<MovimentoCaixa> movimentosDoDia = buscarMovimentosDoDia(filtros.data());

        List<MovimentoCaixaDetalheDTO> movimentosDetalhados = processarMovimentos(movimentosDoDia, saldoInicial);
        ResumoCaixaDTO resumo = calcularResumo(saldoInicial, movimentosDoDia);
        List<ResumoFormaPagamentoDTO> resumoPorFormaPagamento = calcularResumoPorFormaPagamento(movimentosDoDia);
        List<ResumoPorTipoDTO> resumoPorTipo = calcularResumoPorTipo(movimentosDoDia);

        String usuarioGeracao;
        try {
            usuarioGeracao = securityUtils.getUsuarioAtual().getNome();
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário atual: {}", e.getMessage());
            usuarioGeracao = "Sistema";
        }

        return new RelatorioMovimentoCaixaDTO(
                empresa,
                filtros,
                resumo,
                movimentosDetalhados,
                resumoPorFormaPagamento,
                resumoPorTipo,
                LocalDateTime.now(),
                usuarioGeracao
        );
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
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("data:")) return trimmed;
        String sanitized = trimmed.replaceAll("\\s+", "");
        return "data:image/png;base64," + sanitized;
    }

    private BigDecimal calcularSaldoInicial(LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        return movimentoCaixaRepository.calcularSaldoAteData(inicioDia);
    }

    private List<MovimentoCaixa> buscarMovimentosDoDia(LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(23, 59, 59);
        return movimentoCaixaRepository.findMovimentosAfetamCaixaPorPeriodo(inicio, fim);
    }

    private List<MovimentoCaixaDetalheDTO> processarMovimentos(List<MovimentoCaixa> movimentos, BigDecimal saldoInicial) {
        List<MovimentoCaixaDetalheDTO> detalhes = new ArrayList<>();

        // Ordenar por data/hora crescente para o saldo evoluir corretamente
        List<MovimentoCaixa> ordenados = movimentos.stream()
                .sorted(Comparator.comparing(MovimentoCaixa::getDataHora))
                .toList();

        BigDecimal saldoCorrente = saldoInicial;

        for (MovimentoCaixa movimento : ordenados) {
            BigDecimal entrada = null;
            BigDecimal saida = null;

            if (movimento.getOperacao() == TipoOperacao.ENTRADA) {
                entrada = movimento.getValor();
                saldoCorrente = saldoCorrente.add(entrada);
            } else {
                saida = movimento.getValor();
                saldoCorrente = saldoCorrente.subtract(saida);
            }

            detalhes.add(new MovimentoCaixaDetalheDTO(
                    movimento.getDataHora(),
                    getTipoDescricao(movimento.getTipo()),
                    getDescricaoMovimento(movimento),
                    movimento.getMeioPagamento().getDescricao(),
                    movimento.getResponsavel() != null ? movimento.getResponsavel().getNome() : "Sistema",
                    entrada,
                    saida,
                    saldoCorrente
            ));
        }

        return detalhes;
    }

    private String getTipoDescricao(TipoMovimentoCaixa tipo) {
        if (tipo == null) return "Indefinido";
        return switch (tipo) {
            case PAGAMENTO_MESA -> "Pagamento de Mesa";
            case GORJETA -> "Gorjeta";
            case CAIXA_INICIAL -> "Caixa Inicial";
            case REFORCO -> "Reforço";
            case SANGRIA -> "Sangria";
            case CONTAS_PAGAR -> "Contas a Pagar";
            case CONTAS_RECEBER -> "Contas a Receber";
            case ESTORNO -> "Estorno";
            case OUTROS -> "Outros";
        };
    }

    private String getDescricaoMovimento(MovimentoCaixa movimento) {
        String descricao = movimento.getObservacao();
        if (descricao == null || descricao.isBlank()) {
            if (movimento.getReferenciaTipo() != null && movimento.getReferenciaId() != null) {
                descricao = movimento.getReferenciaTipo() + " #" + movimento.getReferenciaId();
            } else {
                descricao = getTipoDescricao(movimento.getTipo());
            }
        }
        return descricao;
    }

    private ResumoCaixaDTO calcularResumo(BigDecimal saldoInicial, List<MovimentoCaixa> movimentos) {
        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSaidas = BigDecimal.ZERO;

        for (MovimentoCaixa movimento : movimentos) {
            if (movimento.getOperacao() == TipoOperacao.ENTRADA) {
                totalEntradas = totalEntradas.add(movimento.getValor());
            } else {
                totalSaidas = totalSaidas.add(movimento.getValor());
            }
        }

        BigDecimal saldoFinal = saldoInicial.add(totalEntradas).subtract(totalSaidas);
        return new ResumoCaixaDTO(saldoInicial, totalEntradas, totalSaidas, saldoFinal);
    }

    private List<ResumoFormaPagamentoDTO> calcularResumoPorFormaPagamento(List<MovimentoCaixa> movimentos) {
        Map<TipoFormaPagamento, BigDecimal> totaisPorForma = new HashMap<>();

        movimentos.stream()
                .filter(m -> m.getOperacao() == TipoOperacao.ENTRADA)
                .forEach(m -> totaisPorForma.merge(m.getMeioPagamento(), m.getValor(), BigDecimal::add));

        return totaisPorForma.entrySet().stream()
                .map(entry -> new ResumoFormaPagamentoDTO(entry.getKey().getDescricao(), null, entry.getValue()))
                .sorted((a, b) -> b.valor().compareTo(a.valor()))
                .collect(Collectors.toList());
    }

    private List<ResumoPorTipoDTO> calcularResumoPorTipo(List<MovimentoCaixa> movimentos) {
        Map<TipoMovimentoCaixa, List<MovimentoCaixa>> movimentosPorTipo = movimentos.stream()
                .collect(Collectors.groupingBy(MovimentoCaixa::getTipo));

        return movimentosPorTipo.entrySet().stream()
                .map(entry -> {
                    TipoMovimentoCaixa tipo = entry.getKey();
                    List<MovimentoCaixa> movimentosTipo = entry.getValue();
                    BigDecimal valor = movimentosTipo.stream()
                            .map(MovimentoCaixa::getValor)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ResumoPorTipoDTO(getTipoDescricao(tipo), movimentosTipo.size(), valor);
                })
                .sorted((a, b) -> b.valor().compareTo(a.valor()))
                .collect(Collectors.toList());
    }

    private String getLogoAsBase64(String logoPath) {
        try {
            File logoFile = new File(logoPath);
            byte[] fileContent = java.nio.file.Files.readAllBytes(logoFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileContent);

            String mimeType = "image/png";
            String lowerPath = logoPath.toLowerCase(Locale.ROOT);
            if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }

            return "data:" + mimeType + ";base64," + base64;
        } catch (Exception e) {
            log.warn("Não foi possível carregar o logo para o relatório: {}", e.getMessage());
            return null;
        }
    }
}

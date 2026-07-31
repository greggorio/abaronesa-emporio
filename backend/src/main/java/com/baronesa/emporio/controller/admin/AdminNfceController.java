package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.StatusCobranca;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.nfe.model.VendaItem;
import com.baronesa.emporio.nfe.model.VendaPagamento;
import com.baronesa.emporio.nfe.dto.DanfceModel;
import com.baronesa.emporio.nfe.service.NfceEmissionService;
import com.baronesa.emporio.nfe.service.NfceParserService;
import com.baronesa.emporio.nfe.service.DanfcePdfGeneratorService;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.NfeRepository;
import com.baronesa.emporio.repository.SessaoCobrancaRepository;
import com.baronesa.emporio.service.PdfGeneratorService;
import com.baronesa.emporio.service.EmailService;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.print.PrintWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/nfce")
@RequiredArgsConstructor
public class AdminNfceController {

    private final NfceEmissionService nfceEmissionService;
    private final PdfGeneratorService pdfGeneratorService;
    private final ConfigManager configManager;
    private final SessaoCobrancaRepository sessaoCobrancaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PrintWebSocketHandler printWebSocketHandler;
    private final NfeRepository nfeRepository;
    private final NfceParserService nfceParserService;
    private final DanfcePdfGeneratorService danfcePdfGeneratorService;
    private final EmailService emailService;
    private final ClienteRepository clienteRepository;

    @GetMapping("/pagamentos/{pagamentoId}/preview")
    public ResponseEntity<Map<String, Object>> preview(@PathVariable Long pagamentoId) {
        Venda venda = nfceEmissionService.prepararVenda(pagamentoId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pagamentoId", pagamentoId);
        body.put("valorTotal", venda.getValorTotal());
        body.put("taxaServico", venda.getAcrescimoTotal());
        body.put("statusVenda", venda.getStatus());
        body.put("statusNfe", venda.getStatusNfe());
        body.put("observacoes", venda.getObservacoes());
        body.put("itens", mapearItens(venda.getItens()));
        body.put("pagamentos", mapearPagamentos(venda.getPagamentos()));
        if (venda.getCliente() != null) {
            body.put("clienteId", venda.getCliente().getId());
            body.put("clienteNome", venda.getCliente().getNome());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/pagamentos/{pagamentoId}/emitir")
    public ResponseEntity<Map<String, Object>> emitir(
            @PathVariable Long pagamentoId,
            @RequestBody(required = false) Map<String, Object> payload
    ) throws Exception {
        String cpf = payload != null ? (String) payload.get("cpf") : null;
        NfeModel nfeModel = nfceEmissionService.emitirNfce(pagamentoId, cpf);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("nfeId", nfeModel.getId());
        response.put("numero", nfeModel.getNumero());
        response.put("status", nfeModel.getStatus());
        response.put("chaveAcesso", nfeModel.getChaveAcesso());
        response.put("motivoRejeicao", nfeModel.getMotivoRejeicao());
        response.put("modelo", nfeModel.getModelo());
        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> mapearItens(List<VendaItem> itens) {
        return itens.stream()
                .map(item -> {
                    Map<String, Object> objeto = new LinkedHashMap<>();
                    objeto.put("descricao", item.getDescricaoProduto());
                    objeto.put("codigo", item.getCodigoProduto());
                    objeto.put("quantidade", item.getQuantidade());
                    objeto.put("valorUnitario", item.getValorUnitario());
                    objeto.put("valorTotal", item.getValorTotalSeguro());
                    objeto.put("ncm", item.getNcm());
                    objeto.put("cfop", item.getCfop());
                    objeto.put("cst", item.getCst());
                    return objeto;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> mapearPagamentos(List<VendaPagamento> pagamentos) {
        return pagamentos.stream()
                .map(p -> {
                    Map<String, Object> objeto = new LinkedHashMap<>();
                    objeto.put("tipo", p.getTipoPagamento().getDescricao());
                    objeto.put("valor", p.getValorSeguro());
                    objeto.put("dataPagamento", p.getDataPagamento());
                    objeto.put("codigoAutorizacao", p.getCodigoAutorizacao());
                    return objeto;
                })
                .collect(Collectors.toList());
    }

    private byte[] buildComprovantePdf(Long pagamentoId) throws Exception {
        Venda venda = nfceEmissionService.prepararVenda(pagamentoId);
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));

        java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("venda", venda);
        context.put("pagamentoId", pagamentoId);
        context.put("nomeCliente", resolverNomeCliente(venda, pagamento));
        context.put("documentoCliente", resolverDocumentoCliente(venda, pagamento));

        String razaoSocial = configManager.getConfig("nfe_razao_social", "EMPRESA NÃO CONFIGURADA");
        String cnpj = configManager.getConfig("nfe_cnpj", "00.000.000/0000-00");

        String endereco = String.format("%s, %s - %s, %s/%s",
                configManager.getConfig("nfe_logradouro", ""),
                configManager.getConfig("nfe_numero", ""),
                configManager.getConfig("nfe_bairro", ""),
                configManager.getConfig("nfe_municipio", ""),
                configManager.getConfig("nfe_uf", "")
        );

        if (endereco.equals(",  - , /")) {
            endereco = "Endereço não configurado";
        }

        context.put("empresaRazaoSocial", razaoSocial);
        context.put("empresaCnpj", cnpj);
        context.put("empresaEndereco", endereco);

        BigDecimal valorTaxaServico = Optional.ofNullable(venda.getAcrescimoTotal()).orElse(BigDecimal.ZERO);
        BigDecimal subtotal = Optional.ofNullable(venda.getSubtotal()).orElse(BigDecimal.ZERO);
        BigDecimal percentualTaxaServico = null;

        String pctConfig = configManager.getConfig("taxa_servico_percentual", "");
        if (pctConfig != null && !pctConfig.isBlank()) {
            try {
                percentualTaxaServico = new BigDecimal(pctConfig.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        if (percentualTaxaServico == null && subtotal.compareTo(BigDecimal.ZERO) > 0
                && valorTaxaServico.compareTo(BigDecimal.ZERO) > 0) {
            percentualTaxaServico = valorTaxaServico
                    .multiply(BigDecimal.valueOf(100))
                    .divide(subtotal, 2, RoundingMode.HALF_UP);
        }

        context.put("valorTaxaServico", valorTaxaServico);
        context.put("percentualTaxaServico", percentualTaxaServico);
        context.put("subtotal", subtotal);

        BigDecimal valorCouvert = BigDecimal.ZERO;

        if (pagamento.getSessaoConvidado() != null) {
            List<com.baronesa.emporio.entity.SessaoCobranca> cobrancas =
                    sessaoCobrancaRepository.findBySessaoConvidadoIdAndStatus(
                            pagamento.getSessaoConvidado().getId(), StatusCobranca.ATIVA);

            for (com.baronesa.emporio.entity.SessaoCobranca cobranca : cobrancas) {
                if (cobranca.getTipo() == com.baronesa.emporio.enums.TipoCobranca.COUVERT_ARTISTICO && !cobranca.getIsento()) {
                    valorCouvert = valorCouvert.add(cobranca.getValor());
                }
            }
        } else if (pagamento.getSessaoMesa() != null) {
            List<com.baronesa.emporio.entity.SessaoCobranca> cobrancas =
                    sessaoCobrancaRepository.findBySessaoMesaIdAndStatus(
                            pagamento.getSessaoMesa().getId(), StatusCobranca.ATIVA);

            for (com.baronesa.emporio.entity.SessaoCobranca cobranca : cobrancas) {
                if (cobranca.getTipo() == com.baronesa.emporio.enums.TipoCobranca.COUVERT_ARTISTICO && !cobranca.getIsento()) {
                    valorCouvert = valorCouvert.add(cobranca.getValor());
                }
            }
        }

        context.put("valorCouvert", valorCouvert);
        context.put("totalGeral", Optional.ofNullable(venda.getValorTotal()).orElse(BigDecimal.ZERO).add(valorCouvert));

        int baseHeight = 60;
        int itemHeight = 10;
        int pagamentoHeight = 5;
        int itensCount = venda.getItens() != null ? venda.getItens().size() : 0;
        int itensMin = Math.max(3, itensCount);
        int espacoItensMm = Math.max(0, 3 - itensCount) * itemHeight;
        int extraAlturaItens = calcularExtraAlturaItens(venda);
        int extraBuffer = itensCount <= 3 ? 10 : 0;
        int totalHeight = baseHeight +
                (itensMin * itemHeight) +
                extraAlturaItens +
                (venda.getPagamentos().size() * pagamentoHeight) +
                (valorCouvert.compareTo(BigDecimal.ZERO) > 0 ? 5 : 0) +
                (valorTaxaServico.compareTo(BigDecimal.ZERO) > 0 ? 5 : 0) +
                extraBuffer;

        context.put("pageHeight", totalHeight);
        context.put("espacoItensMm", espacoItensMm);

        return pdfGeneratorService.generatePdfFromTemplate("comprovante-nao-fiscal", context);
    }

    private byte[] buildDanfcePdf(Long pagamentoId) throws Exception {
        Optional<NfeModel> nfeOpt = nfeRepository.findByIdVenda(pagamentoId);
        NfeModel nfe = nfeOpt.orElseThrow(() -> new IllegalArgumentException("NFC-e não encontrada para este pagamento: " + pagamentoId));
        DanfceModel danfce = nfceParserService.parseNfceXml(nfe);
        return danfcePdfGeneratorService.generateDanfcePdf(danfce);
    }

    private String resolverNomeCliente(Venda venda, Pagamento pagamento) {
        if (venda != null && venda.getCliente() != null && venda.getCliente().getNome() != null) {
            String nome = venda.getCliente().getNome();
            if (!isNomePlaceholder(nome)) {
                return nome;
            }
        }
        if (pagamento != null && pagamento.getSessaoConvidado() != null) {
            String nome = pagamento.getSessaoConvidado().getNomeExibicao();
            if (nome != null && !nome.isBlank() && !isNomePlaceholder(nome)) {
                return nome;
            }
        }
        if (pagamento != null && pagamento.getPagante() != null) {
            String nome = pagamento.getPagante().getNomeExibicao();
            if (nome != null && !nome.isBlank() && !isNomePlaceholder(nome)) {
                return nome;
            }
        }
        return "Consumidor";
    }

    private boolean isNomePlaceholder(String nome) {
        if (nome == null) return true;
        String normalized = nome.trim();
        if (normalized.isEmpty()) return true;
        return normalized.equalsIgnoreCase("balcao") || normalized.equalsIgnoreCase("balcão");
    }

    private String resolverDocumentoCliente(Venda venda, Pagamento pagamento) {
        Usuario usuario = null;
        if (venda != null && venda.getCliente() != null) {
            usuario = venda.getCliente();
        } else if (pagamento != null && pagamento.getSessaoConvidado() != null) {
            usuario = pagamento.getSessaoConvidado().getUsuario();
        } else if (pagamento != null && pagamento.getPagante() != null) {
            usuario = pagamento.getPagante().getUsuario();
        }
        if (usuario == null) return null;

        String documento = null;
        var usuarioComPerfil = clienteRepository.findByIdWithPerfilCliente(usuario.getId(), Usuario.Role.CLIENTE);
        if (usuarioComPerfil.isPresent() && usuarioComPerfil.get().getPerfilCliente() != null) {
            var perfil = usuarioComPerfil.get().getPerfilCliente();
            documento = perfil.getCnpj() != null && !perfil.getCnpj().isBlank()
                    ? formatarCnpj(perfil.getCnpj())
                    : (perfil.getCpf() != null && !perfil.getCpf().isBlank()
                        ? formatarCpf(perfil.getCpf())
                        : null);
        } else if (usuario.getPerfilCliente() != null) {
            var perfil = usuario.getPerfilCliente();
            documento = perfil.getCnpj() != null && !perfil.getCnpj().isBlank()
                    ? formatarCnpj(perfil.getCnpj())
                    : (perfil.getCpf() != null && !perfil.getCpf().isBlank()
                        ? formatarCpf(perfil.getCpf())
                        : null);
        }
        return documento;
    }

    private int calcularExtraAlturaItens(Venda venda) {
        if (venda == null || venda.getItens() == null) return 0;
        int extra = 0;
        for (var item : venda.getItens()) {
            String descricao = item.getDescricaoProduto();
            if (descricao == null) continue;
            String trimmed = descricao.trim();
            if (trimmed.isEmpty()) continue;
            int charsPerLine = 32;
            int lines = (int) Math.ceil(trimmed.length() / (double) charsPerLine);
            if (lines > 1) {
                extra += (lines - 1) * 4;
            }
        }
        return extra;
    }

    private String formatarCpf(String cpf) {
        if (cpf == null) return null;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return cpf;
        return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private String formatarCnpj(String cnpj) {
        if (cnpj == null) return null;
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) return cnpj;
        return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    @GetMapping("/pagamentos/{pagamentoId}/comprovante")
    public ResponseEntity<byte[]> gerarComprovante(@PathVariable Long pagamentoId) throws Exception {
        byte[] pdfContent = buildComprovantePdf(pagamentoId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=comprovante_" + pagamentoId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfContent);
    }

    @PostMapping("/pagamentos/{pagamentoId}/comprovante/print")
    public ResponseEntity<Map<String, Object>> printComprovante(@PathVariable Long pagamentoId) throws Exception {
        return sendPdfToPrintAgent(buildComprovantePdf(pagamentoId), "comprovante-" + pagamentoId, pagamentoId);
    }

    @PostMapping("/pagamentos/{pagamentoId}/danfce/print")
    public ResponseEntity<Map<String, Object>> printDanfce(@PathVariable Long pagamentoId) throws Exception {
        byte[] pdfBytes = buildDanfcePdf(pagamentoId);
        return sendPdfToPrintAgent(pdfBytes, "danfce-" + pagamentoId, pagamentoId);
    }

    private ResponseEntity<Map<String, Object>> sendPdfToPrintAgent(byte[] pdfContent, String jobPrefix, Long pagamentoId) {
        String jobId = jobPrefix + "-" + System.currentTimeMillis();
        String pdfBase64 = Base64.getEncoder().encodeToString(pdfContent);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("pdf_bytes", pdfBase64);
        payload.put("numero_pedido", pagamentoId);

        java.util.Map<String, Object> job = new java.util.LinkedHashMap<>();
        job.put("id", jobId);
        job.put("route", "FISCAL");
        job.put("tipo", "DANFCE");
        job.put("format", "PDF");
        job.put("copies", 1);
        job.put("idempotency_key", jobId);
        job.put("payload", payload);

        try {
            printWebSocketHandler.sendPrintJob(job);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "agent_not_connected", ex);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "job_id", jobId
        ));
    }

    /**
     * Envia a DANFCE por email gerando o PDF e anexando-o.
     */
    @PostMapping("/pagamentos/{pagamentoId}/email")
    public ResponseEntity<Map<String, Object>> enviarDanfcePorEmail(
            @PathVariable Long pagamentoId,
            @RequestBody Map<String, String> payload
    ) throws Exception {
        String email = payload != null ? payload.get("email") : null;
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email é obrigatório"));
        }

        // Buscar NFC-e vinculada ao pagamento
        Optional<NfeModel> nfeOpt = nfeRepository.findByIdVenda(pagamentoId);
        if (nfeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "NFC-e não encontrada para este pagamento"));
        }

        NfeModel nfe = nfeOpt.get();

        // Gerar PDF DANFCE
        DanfceModel danfce = nfceParserService.parseNfceXml(nfe);
        byte[] pdfBytes = danfcePdfGeneratorService.generateDanfcePdf(danfce);

        String assunto = "Seu comprovante fiscal";
        String mensagem = "Segue em anexo o DANFCE da sua compra.";
        String fileName = String.format("danfce_%s.pdf", nfe.getNumero() != null ? nfe.getNumero() : nfe.getId());

        emailService.sendPdf(email, assunto, mensagem, pdfBytes, fileName);

        return ResponseEntity.ok(Map.of("success", true));
    }
}

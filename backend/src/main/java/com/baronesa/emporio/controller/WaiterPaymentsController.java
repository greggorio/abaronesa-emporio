package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.enums.StatusCobranca;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.nfe.service.NfceEmissionService;
import com.baronesa.emporio.repository.ClienteRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.SessaoCobrancaRepository;
import com.baronesa.emporio.service.PdfGeneratorService;
import com.baronesa.emporio.service.ContaService;
import com.baronesa.emporio.service.SessaoMesaService;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@PreAuthorize("hasAnyRole('WAITER','CAIXA','ADMIN','SYSTEM')")
@RequestMapping("/api/waiter")
@RequiredArgsConstructor
public class WaiterPaymentsController {

    private final PagamentoRepository pagamentoRepository;
    private final SessaoMesaService sessaoMesaService;
    private final ContaService contaService;
    private final NfceEmissionService nfceEmissionService;
    private final PdfGeneratorService pdfGeneratorService;
    private final ConfigManager configManager;
    private final SessaoCobrancaRepository sessaoCobrancaRepository;
    private final ClienteRepository clienteRepository;

    @GetMapping("/pagamentos")
    public ResponseEntity<List<WaiterPaymentResponse>> listarPagamentos(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean resolvido
    ) {
        List<StatusPagamento> statuses;
        if (StringUtils.hasText(status)) {
            statuses = List.of(StatusPagamento.valueOf(status.toUpperCase()));
        } else {
            statuses = List.of(StatusPagamento.PENDING, StatusPagamento.PAID);
        }
        Boolean resolvedFlag = resolvido != null ? resolvido : Boolean.FALSE;
        List<Pagamento> pagamentos = pagamentoRepository
                .findTop50BySessaoMesaIsNotNullAndSelfCheckoutOrigemAndSelfCheckoutResolvidoAndStatusInOrderByCriadoEmDesc(
                        "SELF_CHECKOUT",
                        resolvedFlag,
                        statuses
                );
        List<WaiterPaymentResponse> response = pagamentos.stream()
                .map(this::mapWaiterPayment)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mesas/{sessaoMesaId}/fechar")
    public ResponseEntity<Map<String, Object>> fecharMesa(@PathVariable Long sessaoMesaId) {
        sessaoMesaService.fecharSessao(sessaoMesaId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/pagamentos/{pagamentoId}/resolver")
    public ResponseEntity<Map<String, Object>> resolverPagamento(
            @PathVariable Long pagamentoId,
            @RequestParam(defaultValue = "false") boolean fecharMesa
    ) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));
        pagamento.setSelfCheckoutResolvido(Boolean.TRUE);
        pagamento.setSelfCheckoutResolvidoEm(java.time.LocalDateTime.now());
        pagamentoRepository.save(pagamento);

        if (fecharMesa && pagamento.getSessaoMesa() != null) {
            try {
                var conta = contaService.contaMesa(pagamento.getSessaoMesa().getId());
                if (conta.devidoTotalCentavos() == 0) {
                    sessaoMesaService.fecharSessao(pagamento.getSessaoMesa().getId());
                } else {
                    return ResponseEntity.status(409).body(Map.of(
                            "success", false,
                            "message", "Mesa possui saldo pendente"
                    ));
                }
            } catch (com.baronesa.emporio.exception.SessionClosedException ignored) {
                // Mesa já encerrada, tratamos como sucesso.
            }
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/pagamentos/{pagamentoId}/emitir-nfce")
    public ResponseEntity<Map<String, Object>> emitirNfce(@PathVariable Long pagamentoId) throws Exception {
        NfeModel nfeModel = nfceEmissionService.emitirNfce(pagamentoId);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("success", true);
        response.put("nfeId", nfeModel.getId());
        response.put("numero", nfeModel.getNumero());
        response.put("status", nfeModel.getStatus());
        response.put("chaveAcesso", nfeModel.getChaveAcesso());
        response.put("motivoRejeicao", nfeModel.getMotivoRejeicao());
        response.put("modelo", nfeModel.getModelo());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pagamentos/{pagamentoId}/comprovante")
    public ResponseEntity<byte[]> gerarComprovante(@PathVariable Long pagamentoId) throws Exception {
        Venda venda = nfceEmissionService.prepararVenda(pagamentoId);
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));

        Map<String, Object> context = new LinkedHashMap<>();
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
            } catch (NumberFormatException ignored) {}
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
            var cobrancas = sessaoCobrancaRepository.findBySessaoConvidadoIdAndStatus(
                    pagamento.getSessaoConvidado().getId(), StatusCobranca.ATIVA);
            for (var cobranca : cobrancas) {
                if (cobranca.getTipo() == com.baronesa.emporio.enums.TipoCobranca.COUVERT_ARTISTICO && !cobranca.getIsento()) {
                    valorCouvert = valorCouvert.add(cobranca.getValor());
                }
            }
        } else if (pagamento.getSessaoMesa() != null) {
            var cobrancas = sessaoCobrancaRepository.findBySessaoMesaIdAndStatus(
                    pagamento.getSessaoMesa().getId(), StatusCobranca.ATIVA);
            for (var cobranca : cobrancas) {
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

        byte[] pdfContent = pdfGeneratorService.generatePdfFromTemplate("comprovante-nao-fiscal", context);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=comprovante_" + pagamentoId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfContent);
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

    private WaiterPaymentResponse mapWaiterPayment(Pagamento pagamento) {
        String mesaSlug = null;
        String mesaRotulo = null;
        if (pagamento.getSessaoMesa() != null && pagamento.getSessaoMesa().getMesa() != null) {
            mesaSlug = pagamento.getSessaoMesa().getMesa().getSlug();
            mesaRotulo = pagamento.getSessaoMesa().getMesa().getRotulo();
        }
        String convidado = pagamento.getSessaoConvidado() != null ? pagamento.getSessaoConvidado().getNomeExibicao() : null;
        String pagante = pagamento.getPagante() != null ? pagamento.getPagante().getNomeExibicao() : null;
        return new WaiterPaymentResponse(
                pagamento.getId(),
                pagamento.getSessaoMesa() != null ? pagamento.getSessaoMesa().getId() : null,
                mesaSlug,
                mesaRotulo,
                pagamento.getSessaoConvidado() != null ? pagamento.getSessaoConvidado().getId() : null,
                convidado,
                pagante,
                pagamento.getMetodo(),
                pagamento.getStatus() != null ? pagamento.getStatus().name().toLowerCase() : null,
                pagamento.getValor(),
                pagamento.getCriadoEm(),
                pagamento.getPagoEm(),
                pagamento.getSelfCheckoutResolvido()
        );
    }

    private record WaiterPaymentResponse(
            Long pagamentoId,
            Long sessaoMesaId,
            String mesaSlug,
            String mesaRotulo,
            Long sessaoConvidadoId,
            String convidado,
            String pagante,
            String metodo,
            String status,
            BigDecimal valor,
            java.time.LocalDateTime criadoEm,
            java.time.LocalDateTime pagoEm,
            Boolean resolvido
    ) {}
}

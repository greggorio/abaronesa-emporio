package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.entity.NfeModel;
import com.baronesa.emporio.entity.PerfilCliente;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TipoPessoa;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.nfe.builder.XmlNFCeBuilder;
import com.baronesa.emporio.nfe.builder.XmlNFeBuilder;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.nfe.service.ModeloFiscalDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra a emissão de NFC-e para pagamentos (integra a factory com o fluxo que será portado).
 * Por enquanto apenas prepara a Venda; o envio à SEFAZ será conectado ao NfeService legado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NfceEmissionService {

    private final VendaFiscalFactory vendaFiscalFactory;
    private final XmlNFCeBuilder xmlNFCeBuilder;
    private final XmlNFeBuilder xmlNFeBuilder;
    private final ModeloFiscalDetector modeloFiscalDetector;

    /**
     * Prepara a venda fiscal para um pagamento, sem emitir a nota.
     */
    public Venda prepararVenda(Long pagamentoId) {
        Venda venda = vendaFiscalFactory.criarVendaParaPagamento(pagamentoId);
        log.debug("Venda fiscal preparada para pagamento {} com {} itens", pagamentoId, venda.getItens().size());
        return venda;
    }

    /**
     * Ponto único para emissão da NFC-e. Neste momento apenas retorna a venda pronta;
     * em seguida será ligado ao NfeService portado.
     */
    public NfeModel emitirNfce(Long pagamentoId) throws Exception {
        return emitirNfce(pagamentoId, null);
    }

    public NfeModel emitirNfce(Long pagamentoId, String cpfConsumidor) throws Exception {
        Venda venda = prepararVenda(pagamentoId);
        var modelo = modeloFiscalDetector.detectarModelo(venda);
        String cpfNormalizado = normalizarCpf(cpfConsumidor);

        log.info("Emitindo documento fiscal (modelo {}) para pagamento {} (valor total: {})",
                modelo.getSigla(), pagamentoId, venda.getValorTotal());

        if (modelo == com.baronesa.emporio.nfe.config.ModeloFiscalConfig.NFCE) {
            return xmlNFCeBuilder.gerarNFCeCompleta(pagamentoId, venda, cpfNormalizado);
        }
        validarDadosNfe(venda);
        return xmlNFeBuilder.gerarNFeCompleta(pagamentoId, venda);
    }

    private void validarDadosNfe(Venda venda) {
        if (venda == null) {
            throw new BusinessException("Venda não encontrada para emissão de NFe.");
        }
        Usuario cliente = venda.getCliente();
        if (cliente == null) {
            // Venda sem cliente identificado: emitir como consumidor final
            return;
        }
        if (cliente.getPerfilCliente() == null) {
            throw new BusinessException("Perfil do cliente incompleto para emissão de NFe.");
        }
        PerfilCliente perfil = cliente.getPerfilCliente();

        java.util.List<String> faltantes = new java.util.ArrayList<>();

        TipoPessoa tipoPessoa = perfil.getTipoPessoa();
        if (tipoPessoa == null) {
            faltantes.add("tipo de pessoa");
        } else if (tipoPessoa == TipoPessoa.PJ) {
            if (isBlank(perfil.getCnpj())) faltantes.add("CNPJ");
        } else {
            if (isBlank(perfil.getCpf())) faltantes.add("CPF");
        }

        if (isBlank(perfil.getLogradouro()) && isBlank(perfil.getEndereco())) faltantes.add("logradouro");
        if (isBlank(perfil.getNumero())) faltantes.add("número");
        if (isBlank(perfil.getBairro())) faltantes.add("bairro");
        if (isBlank(perfil.getCidade())) faltantes.add("cidade");
        if (isBlank(perfil.getEstado())) faltantes.add("UF");
        if (isBlank(perfil.getCep())) faltantes.add("CEP");

        if (!faltantes.isEmpty()) {
            throw new BusinessException("Dados do destinatário incompletos: " + String.join(", ", faltantes) + ".");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizarCpf(String cpf) {
        if (cpf == null || cpf.trim().isEmpty()) {
            return null;
        }
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11 || !cpfValido(digits)) {
            throw new BusinessException("CPF inválido.");
        }
        return digits;
    }

    private boolean cpfValido(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return false;
        }
        // Rejeita sequências com todos os dígitos iguais
        char primeiro = cpf.charAt(0);
        boolean todosIguais = true;
        for (int i = 1; i < cpf.length(); i++) {
            if (cpf.charAt(i) != primeiro) {
                todosIguais = false;
                break;
            }
        }
        if (todosIguais) {
            return false;
        }

        int soma = 0;
        for (int i = 0; i < 9; i++) {
            soma += (cpf.charAt(i) - '0') * (10 - i);
        }
        int mod = soma % 11;
        int dv1 = (mod < 2) ? 0 : 11 - mod;
        if (dv1 != (cpf.charAt(9) - '0')) {
            return false;
        }

        soma = 0;
        for (int i = 0; i < 10; i++) {
            soma += (cpf.charAt(i) - '0') * (11 - i);
        }
        mod = soma % 11;
        int dv2 = (mod < 2) ? 0 : 11 - mod;
        return dv2 == (cpf.charAt(10) - '0');
    }
}

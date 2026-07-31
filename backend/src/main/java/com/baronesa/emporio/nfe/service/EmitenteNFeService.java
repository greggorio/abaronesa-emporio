package com.baronesa.emporio.nfe.service;

import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnderEmi;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TUfEmi;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmitenteNFeService {

    private final ConfigManager configManager;

    public TNFe.InfNFe.Emit criarDadosEmitente() throws Exception {
        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        String cnpj = configManager.getConfig("nfe_cnpj", "");
        if (cnpj.isEmpty()) {
            throw new Exception("CNPJ do emitente não configurado");
        }
        emit.setCNPJ(cnpj);

        String razaoSocial = configManager.getConfig("nfe_razao_social", "");
        if (razaoSocial.isEmpty()) {
            throw new Exception("Razão social do emitente não configurada");
        }
        emit.setXNome(razaoSocial);

        String nomeFantasia = configManager.getConfig("nfe_nome_fantasia", "");
        emit.setXFant(nomeFantasia.isEmpty() ? razaoSocial : nomeFantasia);

        String ie = configManager.getConfig("nfe_inscricao_estadual", "");
        if (ie.isEmpty()) {
            throw new Exception("Inscrição estadual do emitente não configurada");
        }
        String ieLimpo = ie.replaceAll("[^0-9]", "");
        if (ieLimpo.isEmpty()) {
            throw new Exception("Inscrição estadual do emitente inválida");
        }
        emit.setIE(ieLimpo);

        emit.setEnderEmit(criarEnderecoEmitente());

        String crt = configManager.getConfig("nfe_regime_tributario", "");
        if (crt.isEmpty()) {
            throw new Exception("Regime tributário do emitente não configurado");
        }
        emit.setCRT(crt);

        return emit;
    }

    private TEnderEmi criarEnderecoEmitente() throws Exception {
        TEnderEmi enderEmit = new TEnderEmi();

        String logradouro = configManager.getConfig("nfe_logradouro", "");
        if (logradouro.isEmpty()) {
            throw new Exception("Logradouro do emitente não configurado");
        }
        enderEmit.setXLgr(logradouro);

        String numero = configManager.getConfig("nfe_numero", "");
        if (numero.isEmpty()) {
            throw new Exception("Número do endereço do emitente não configurado");
        }
        enderEmit.setNro(numero);

        String bairro = configManager.getConfig("nfe_bairro", "");
        if (bairro.isEmpty()) {
            throw new Exception("Bairro do emitente não configurado");
        }
        enderEmit.setXBairro(bairro);

        String codMunicipio = configManager.getConfig("nfe_cod_municipio", "");
        if (codMunicipio.isEmpty()) {
            throw new Exception("Código do município não configurado");
        }
        enderEmit.setCMun(codMunicipio);

        String municipio = configManager.getConfig("nfe_municipio", "");
        if (municipio.isEmpty()) {
            throw new Exception("Município do emitente não configurado");
        }
        enderEmit.setXMun(municipio);

        String uf = configManager.getConfig("nfe_uf", "");
        if (uf.isEmpty()) {
            throw new Exception("UF do emitente não configurada");
        }
        try {
            enderEmit.setUF(TUfEmi.valueOf(uf));
        } catch (IllegalArgumentException e) {
            throw new Exception("UF do emitente inválida: " + uf);
        }

        String cep = configManager.getConfig("nfe_cep", "");
        if (cep.isEmpty()) {
            throw new Exception("CEP do emitente não configurado");
        }
        String cepLimpo = cep.replaceAll("[^0-9]", "");
        if (cepLimpo.length() != 8) {
            throw new Exception("CEP do emitente inválido");
        }
        enderEmit.setCEP(cepLimpo);

        String telefone = configManager.getConfig("nfe_telefone", "");
        if (telefone.isEmpty()) {
            throw new Exception("Telefone do emitente não configurado");
        }
        String telefoneLimpo = telefone.replaceAll("[^0-9]", "");
        if (telefoneLimpo.length() < 6 || telefoneLimpo.length() > 14) {
            throw new Exception("Telefone do emitente inválido");
        }
        enderEmit.setFone(telefoneLimpo);

        String complemento = configManager.getConfig("nfe_complemento", "");
        if (!complemento.isEmpty()) {
            enderEmit.setXCpl(complemento);
        }

        return enderEmit;
    }

    public String getUfCodigoEmitente() throws Exception {
        return estadoParaCodigo(getUfEmitente());
    }

    public String getUfEmitente() throws Exception {
        String uf = configManager.getConfig("nfe_uf", "");
        if (uf.isEmpty()) {
            throw new Exception("UF do emitente não configurada");
        }
        return uf;
    }

    public String getCnpjEmitente() throws Exception {
        String cnpj = configManager.getConfig("nfe_cnpj", "");
        if (cnpj.isEmpty()) {
            throw new Exception("CNPJ do emitente não configurado");
        }
        return cnpj;
    }

    private String estadoParaCodigo(String estado) throws Exception {
        return switch (estado.toUpperCase()) {
            case "AC" -> "12";
            case "AL" -> "27";
            case "AP" -> "16";
            case "AM" -> "13";
            case "BA" -> "29";
            case "CE" -> "23";
            case "DF" -> "53";
            case "ES" -> "32";
            case "GO" -> "52";
            case "MA" -> "21";
            case "MT" -> "51";
            case "MS" -> "50";
            case "MG" -> "31";
            case "PA" -> "15";
            case "PB" -> "25";
            case "PR" -> "41";
            case "PE" -> "26";
            case "PI" -> "22";
            case "RJ" -> "33";
            case "RN" -> "24";
            case "RS" -> "43";
            case "RO" -> "11";
            case "RR" -> "14";
            case "SC" -> "42";
            case "SP" -> "35";
            case "SE" -> "28";
            case "TO" -> "17";
            default -> throw new Exception("Estado inválido: " + estado);
        };
    }

    public String gerarXmlEmitente() throws Exception {
        TNFe.InfNFe.Emit emitente = criarDadosEmitente();
        StringBuilder xmlEmit = new StringBuilder();
        xmlEmit.append("            <emit>\n")
                .append("                <CNPJ>").append(emitente.getCNPJ()).append("</CNPJ>\n")
                .append("                <xNome>").append(emitente.getXNome()).append("</xNome>\n")
                .append("                <xFant>").append(emitente.getXFant()).append("</xFant>\n")
                .append("                <enderEmit>\n")
                .append("                    <xLgr>").append(emitente.getEnderEmit().getXLgr()).append("</xLgr>\n")
                .append("                    <nro>").append(emitente.getEnderEmit().getNro()).append("</nro>\n");
        if (emitente.getEnderEmit().getXCpl() != null && !emitente.getEnderEmit().getXCpl().isEmpty()) {
            xmlEmit.append("                    <xCpl>").append(emitente.getEnderEmit().getXCpl()).append("</xCpl>\n");
        }
        xmlEmit.append("                    <xBairro>").append(emitente.getEnderEmit().getXBairro()).append("</xBairro>\n")
                .append("                    <cMun>").append(emitente.getEnderEmit().getCMun()).append("</cMun>\n")
                .append("                    <xMun>").append(emitente.getEnderEmit().getXMun()).append("</xMun>\n")
                .append("                    <UF>").append(emitente.getEnderEmit().getUF()).append("</UF>\n")
                .append("                    <CEP>").append(emitente.getEnderEmit().getCEP()).append("</CEP>\n");
        if (emitente.getEnderEmit().getFone() != null && !emitente.getEnderEmit().getFone().isEmpty()) {
            xmlEmit.append("                    <fone>").append(emitente.getEnderEmit().getFone()).append("</fone>\n");
        }
        xmlEmit.append("                </enderEmit>\n")
                .append("                <IE>").append(emitente.getIE()).append("</IE>\n")
                .append("                <CRT>").append(emitente.getCRT()).append("</CRT>\n")
                .append("            </emit>\n");
        return xmlEmit.toString();
    }
}

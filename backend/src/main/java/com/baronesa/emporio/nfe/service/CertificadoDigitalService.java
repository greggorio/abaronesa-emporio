package com.baronesa.emporio.nfe.service;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

@Service
@RequiredArgsConstructor
public class CertificadoDigitalService {

    private final ConfigManager configManager;

    public KeyStore carregarCertificado(String caminhoCertificado, String senhaCertificado) throws Exception {
        try {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(new FileInputStream(caminhoCertificado), senhaCertificado.toCharArray());
            return keystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new Exception("Erro ao carregar certificado: " + e.getMessage(), e);
        }
    }

    public String obterAliasCertificado(KeyStore keystore) throws Exception {
        try {
            Enumeration<String> aliases = keystore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keystore.isKeyEntry(alias)) {
                    return alias;
                }
            }
            throw new Exception("Nenhum certificado válido encontrado no keystore");
        } catch (KeyStoreException e) {
            throw new Exception("Erro ao buscar certificado: " + e.getMessage(), e);
        }
    }

    public PrivateKey obterChavePrivada(KeyStore keystore, String alias, String senha) throws Exception {
        try {
            return (PrivateKey) keystore.getKey(alias, senha.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new Exception("Erro ao obter chave privada: " + e.getMessage(), e);
        }
    }

    public X509Certificate obterCertificadoX509(KeyStore keystore, String alias) throws Exception {
        try {
            Certificate certificate = keystore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509) {
                return x509;
            }
            throw new Exception("O certificado não é do tipo X509Certificate");
        } catch (KeyStoreException e) {
            throw new Exception("Erro ao obter certificado: " + e.getMessage(), e);
        }
    }

    public boolean isCertificadoValido(X509Certificate certificate) {
        try {
            certificate.checkValidity(new Date());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ConfiguracoesNfe inicializarConfiguracoesSefaz() throws Exception {
        return inicializarConfiguracoesSefazInterno(false);
    }

    public ConfiguracoesNfe inicializarConfiguracoesSefazNfce() throws Exception {
        return inicializarConfiguracoesSefazInterno(true);
    }

    private ConfiguracoesNfe inicializarConfiguracoesSefazInterno(boolean nfce) throws Exception {
        try {
            String caminhoCertificado = configManager.getConfig("nfe_certificado_path", "");
            String senhaCertificado = configManager.getConfig("nfe_certificado_senha", "");
            String caminhoSchemas = configManager.getConfig("nfe_schema_path", "");

            String ufSigla = configManager.getConfig("nfe_uf", "SP");
            EstadosEnum estado = EstadosEnum.valueOf(ufSigla);

            String ambiente = nfce
                    ? primeiroNaoVazio(
                    configManager.getConfig("nfce_ambiente", null),
                    configManager.getConfig("nfe_ambiente", "2"))
                    : configManager.getConfig("nfe_ambiente", "2");

            AmbienteEnum ambienteEnum = "1".equals(ambiente)
                    ? AmbienteEnum.PRODUCAO
                    : AmbienteEnum.HOMOLOGACAO;

            Certificado certificado = CertificadoService.certificadoPfx(caminhoCertificado, senhaCertificado);

            return ConfiguracoesNfe.criarConfiguracoes(
                    estado,
                    ambienteEnum,
                    certificado,
                    caminhoSchemas
            );
        } catch (Exception e) {
            throw new Exception("Erro ao inicializar configurações do SEFAZ: " + e.getMessage(), e);
        }
    }

    private String primeiroNaoVazio(String... valores) {
        if (valores == null) return null;
        for (String valor : valores) {
            if (valor != null && !valor.trim().isEmpty()) {
                return valor;
            }
        }
        return null;
    }

    public String obterInformacoesCertificado(String caminhoCertificado, String senhaCertificado) {
        try {
            KeyStore keystore = carregarCertificado(caminhoCertificado, senhaCertificado);
            String alias = obterAliasCertificado(keystore);
            X509Certificate certificate = obterCertificadoX509(keystore, alias);

            StringBuilder info = new StringBuilder();
            info.append("Sujeito: ").append(certificate.getSubjectDN()).append("\n");
            info.append("Emissor: ").append(certificate.getIssuerDN()).append("\n");
            info.append("Validade: De ").append(certificate.getNotBefore())
                    .append(" até ").append(certificate.getNotAfter()).append("\n");
            info.append("Número de série: ").append(certificate.getSerialNumber()).append("\n");
            info.append("Algoritmo de assinatura: ").append(certificate.getSigAlgName());
            return info.toString();
        } catch (Exception e) {
            return "Erro ao obter informações do certificado: " + e.getMessage();
        }
    }
}

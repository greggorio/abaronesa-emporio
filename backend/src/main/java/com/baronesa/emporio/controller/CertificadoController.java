package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.UploadService;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/certificado")
@RequiredArgsConstructor
@Slf4j
public class CertificadoController {

    private final UploadService uploadService;
    private final ConfigManager configManager;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCertificado(@RequestParam("certificado") MultipartFile certificado) {
        try {
            // Validação do tipo de arquivo
            if (certificado == null || certificado.isEmpty()) {
                throw new IllegalArgumentException("Arquivo de certificado inválido ou vazio");
            }

            // Verifica se o arquivo tem extensão .pfx
            String fileName = certificado.getOriginalFilename();
            if (fileName == null) {
                throw new IllegalArgumentException("Nome do arquivo inválido");
            }

            if (!fileName.toLowerCase().endsWith(".pfx")) {
                throw new IllegalArgumentException("Formato de certificado inválido. Apenas arquivos .pfx são aceitos.");
            }

            // Usa o UploadService para salvar o certificado
            String caminhoAbsoluto = uploadService.salvarCertificadoDigital(certificado);

            // Atualiza a configuração do caminho do certificado
            configManager.setConfig("nfe_certificado_path", caminhoAbsoluto);

            // Prepara resposta
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Certificado digital enviado e configurado com sucesso!");
            response.put("path", caminhoAbsoluto);
            response.put("size", certificado.getSize());

            log.info("Certificado digital enviado: {} ({} bytes)", fileName, certificado.getSize());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao salvar certificado digital: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao salvar o certificado digital: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfoCertificado() {
        Map<String, Object> response = new HashMap<>();

        String caminhoCertificado = configManager.getConfig("nfe_certificado_path", "");

        response.put("caminho_atual", caminhoCertificado);
        response.put("existe_arquivo", !caminhoCertificado.isEmpty() && new File(caminhoCertificado).exists());

        if (!caminhoCertificado.isEmpty()) {
            File certFile = new File(caminhoCertificado);
            if (certFile.exists()) {
                response.put("tamanho", certFile.length());
                response.put("ultima_modificacao", certFile.lastModified());
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/validar")
    public ResponseEntity<Map<String, Object>> validarCertificado(@RequestParam(value = "certificado", required = false) MultipartFile certificado) {
        Map<String, Object> response = new HashMap<>();

        try {
            String caminhoCertificado;

            if (certificado != null) {
                // Se um arquivo foi enviado para validação, validamos esse arquivo temporariamente
                String tempPath = uploadService.salvarCertificadoDigital(certificado);
                caminhoCertificado = tempPath;
            } else {
                // Caso contrário, usamos o certificado configurado
                caminhoCertificado = configManager.getConfig("nfe_certificado_path", "");
            }

            if (caminhoCertificado.isEmpty()) {
                response.put("valid", false);
                response.put("message", "Nenhum certificado encontrado para validação");
                return ResponseEntity.ok(response);
            }

            File certFile = new File(caminhoCertificado);
            if (!certFile.exists()) {
                response.put("valid", false);
                response.put("message", "Arquivo do certificado não encontrado");
                return ResponseEntity.ok(response);
            }

            // Validar o certificado
            Map<String, Object> certInfo = validarCertificadoNoCaminho(caminhoCertificado);

            response.put("valid", certInfo.get("valid"));
            response.put("message", (Boolean) certInfo.get("valid") ? "Certificado válido" : "Certificado inválido");
            response.put("info", certInfo);

            // Se o certificado foi temporário (upload para validação), removê-lo
            if (certificado != null) {
                certFile.delete();
            }

        } catch (Exception e) {
            log.error("Erro ao validar certificado: {}", e.getMessage());
            response.put("valid", false);
            response.put("message", "Erro ao validar certificado: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> validarCertificadoNoCaminho(String caminhoCertificado) throws Exception {
        Map<String, Object> result = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(caminhoCertificado)) {
            // Tenta carregar o KeyStore
            KeyStore ks = KeyStore.getInstance("PKCS12");

            // A senha do certificado pode ser obtida da configuração ou ser vazia
            String senhaCertificado = configManager.getConfig("nfe_certificado_senha", "");
            ks.load(fis, senhaCertificado.toCharArray());

            // Verificar se há aliases
            String alias = null;
            java.util.Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String a = aliases.nextElement();
                if (ks.isKeyEntry(a)) {
                    alias = a;
                    break;
                }
            }

            if (alias == null) {
                result.put("valid", false);
                result.put("error", "Nenhuma chave encontrada no certificado");
                return result;
            }

            // Obter o certificado
            Certificate cert = ks.getCertificate(alias);
            if (cert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) cert;

                // Verificar se o certificado está dentro do período de validade
                Date now = new Date();
                Date notBefore = x509Cert.getNotBefore();
                Date notAfter = x509Cert.getNotAfter();

                boolean dataValida = now.after(notBefore) && now.before(notAfter);

                result.put("valid", dataValida);
                result.put("subject", x509Cert.getSubjectDN().toString());
                result.put("issuer", x509Cert.getIssuerDN().toString());
                result.put("validFrom", notBefore);
                result.put("validUntil", notAfter);
                result.put("serialNumber", x509Cert.getSerialNumber().toString());
                result.put("isValidDate", dataValida);

                if (!dataValida) {
                    if (now.before(notBefore)) {
                        result.put("error", "Certificado ainda não é válido (data de início: " + notBefore + ")");
                    } else {
                        result.put("error", "Certificado expirado em: " + notAfter);
                    }
                }
            } else {
                result.put("valid", false);
                result.put("error", "Certificado não é do tipo X.509");
            }
        } catch (IOException e) {
            result.put("valid", false);
            result.put("error", "Erro de leitura do arquivo: " + e.getMessage());
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Erro ao carregar o certificado: " + e.getMessage());
        }

        return result;
    }
}
package com.baronesa.emporio.service.smtp;

import com.baronesa.emporio.dto.smtp.SmtpConfigDTO;
import com.baronesa.emporio.util.ConfigManager;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Properties;

/**
 * Serviço para gerenciar configurações SMTP dinâmicas
 * Lê configurações do ConfigManager com fallback para application.properties
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpConfigService {

    private final ConfigManager configManager;

    // Valores padrão do application.properties
    @Value("${spring.mail.host:smtp.gmail.com}")
    private String defaultHost;

    @Value("${spring.mail.port:587}")
    private Integer defaultPort;

    @Value("${spring.mail.username:}")
    private String defaultUsername;

    @Value("${spring.mail.password:}")
    private String defaultPassword;

    @Value("${app.mail.from-name:Sistema}")
    private String defaultFromName;

    // Chave para criptografia simples (em produção, usar algo mais robusto)
    private static final String ENCRYPTION_KEY = "SmtpC0nf1gK3y123";

    /**
     * Busca as configurações SMTP (do ConfigManager ou fallback)
     */
    public SmtpConfigDTO getConfig() {
        String host = configManager.getConfig("smtp_host", defaultHost);
        Integer port = configManager.getIntConfig("smtp_port", defaultPort);
        String username = configManager.getConfig("smtp_username", defaultUsername);
        String encryptedPassword = configManager.getConfig("smtp_password", "");
        String fromName = configManager.getConfig("smtp_from_name", defaultFromName);
        String seguranca = configManager.getConfig("smtp_seguranca", "tls");

        // Descriptografar senha se houver
        String password = encryptedPassword.isEmpty() ? defaultPassword : decryptPassword(encryptedPassword);

        return SmtpConfigDTO.builder()
                .servidor(host)
                .porta(port)
                .seguranca(seguranca)
                .emailRemetente(username)
                .nomeRemetente(fromName)
                .usuario(username)
                .senha(password)
                .build();
    }

    /**
     * Salva as configurações SMTP no ConfigManager
     */
    public boolean saveConfig(SmtpConfigDTO config) {
        try {
            configManager.setConfig("smtp_host", config.getServidor());
            configManager.setConfig("smtp_port", String.valueOf(config.getPorta()));
            configManager.setConfig("smtp_username", config.getUsuario());
            configManager.setConfig("smtp_from_name", config.getNomeRemetente() != null ? config.getNomeRemetente() : "");
            configManager.setConfig("smtp_seguranca", config.getSeguranca() != null ? config.getSeguranca() : "tls");

            // Criptografar senha antes de salvar
            if (config.getSenha() != null && !config.getSenha().isEmpty()) {
                String encryptedPassword = encryptPassword(config.getSenha());
                configManager.setConfig("smtp_password", encryptedPassword);
            }

            return true;
        } catch (Exception e) {
            log.error("Erro ao salvar configurações SMTP", e);
            return false;
        }
    }

    /**
     * Cria um JavaMailSender com as configurações atuais
     */
    public JavaMailSender createMailSender() {
        SmtpConfigDTO config = getConfig();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getServidor());
        mailSender.setPort(config.getPorta());
        mailSender.setUsername(config.getUsuario());
        mailSender.setPassword(config.getSenha());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "false");

        // Configurar segurança baseado no tipo
        if ("tls".equalsIgnoreCase(config.getSeguranca())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else if ("ssl".equalsIgnoreCase(config.getSeguranca())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", config.getPorta());
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        }

        // Timeouts e trust (valores fixos seguros)
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return mailSender;
    }

    /**
     * Testa a conexão SMTP com as configurações fornecidas
     */
    public boolean testConnection(SmtpConfigDTO config) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(config.getServidor());
            mailSender.setPort(config.getPorta());
            mailSender.setUsername(config.getUsuario());
            mailSender.setPassword(config.getSenha());

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");

            if ("tls".equalsIgnoreCase(config.getSeguranca())) {
                props.put("mail.smtp.starttls.enable", "true");
            } else if ("ssl".equalsIgnoreCase(config.getSeguranca())) {
                props.put("mail.smtp.ssl.enable", "true");
            }

            // Tentar conectar
            mailSender.testConnection();
            return true;
        } catch (MessagingException e) {
            log.error("Erro ao testar conexão SMTP: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Criptografa a senha usando AES
     */
    private String encryptPassword(String password) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(password.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Erro ao criptografar senha", e);
            return password; // Fallback: retornar senha original
        }
    }

    /**
     * Descriptografa a senha usando AES
     */
    private String decryptPassword(String encryptedPassword) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedPassword);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            log.error("Erro ao descriptografar senha", e);
            return ""; // Fallback: retornar vazio em caso de erro
        }
    }
}
package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.smtp.SmtpConfigDTO;
import com.baronesa.emporio.service.smtp.SmtpConfigService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para gerenciar configurações SMTP
 */
@Slf4j
@RestController
@RequestMapping("/api/smtp")
@RequiredArgsConstructor
public class SmtpController {

    private final SmtpConfigService smtpConfigService;

    /**
     * Busca as configurações SMTP atuais
     */
    @GetMapping("/config")
    public ResponseEntity<SmtpConfigDTO> getConfig() {
        try {
            SmtpConfigDTO config = smtpConfigService.getConfig();
            // Não retornar a senha completa por segurança
            config.setSenha(config.getSenha() != null && !config.getSenha().isEmpty() ? "********" : "");
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Erro ao buscar configurações SMTP", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Salva as configurações SMTP
     */
    @PutMapping("/salvar")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody SmtpConfigDTO config) {
        try {
            // Se a senha vier como "********", não atualizar (mantém a existente)
            if ("********".equals(config.getSenha())) {
                SmtpConfigDTO currentConfig = smtpConfigService.getConfig();
                config.setSenha(currentConfig.getSenha());
            }

            boolean success = smtpConfigService.saveConfig(config);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", success);
            response.put("mensagem", success ? "Configurações salvas com sucesso" : "Erro ao salvar configurações");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao salvar configurações SMTP", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("mensagem", "Erro ao salvar: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Testa a conexão SMTP com as configurações fornecidas
     */
    @PostMapping("/testar-conexao")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody SmtpConfigDTO config) {
        try {
            // Se a senha vier como "********", usar a senha atual
            if ("********".equals(config.getSenha())) {
                SmtpConfigDTO currentConfig = smtpConfigService.getConfig();
                config.setSenha(currentConfig.getSenha());
            }

            boolean success = smtpConfigService.testConnection(config);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", success);
            response.put("mensagem", success ?
                "Conexão estabelecida com sucesso!" :
                "Falha ao conectar ao servidor SMTP");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao testar conexão SMTP", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("erro", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Envia um e-mail de teste
     */
    @PostMapping("/enviar-teste")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody Map<String, Object> request) {
        try {
            String emailDestino = (String) request.get("email_destino");

            if (emailDestino == null || emailDestino.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("sucesso", false);
                response.put("mensagem", "E-mail de destino não informado");
                return ResponseEntity.badRequest().body(response);
            }

            // Extrair configurações do request ou usar as salvas
            SmtpConfigDTO config;
            if (request.containsKey("servidor")) {
                config = new SmtpConfigDTO();
                config.setServidor((String) request.get("servidor"));
                config.setPorta((Integer) request.get("porta"));
                config.setSeguranca((String) request.get("seguranca"));
                config.setUsuario((String) request.get("usuario"));
                config.setSenha((String) request.get("senha"));
                config.setEmailRemetente((String) request.get("email_remetente"));
                config.setNomeRemetente((String) request.get("nome_remetente"));
            } else {
                config = smtpConfigService.getConfig();
            }

            // Se a senha vier como "********", usar a senha atual
            if ("********".equals(config.getSenha())) {
                SmtpConfigDTO currentConfig = smtpConfigService.getConfig();
                config.setSenha(currentConfig.getSenha());
            }

            // Criar mail sender temporário para teste
            JavaMailSender mailSender = smtpConfigService.createMailSender();

            // Criar e enviar mensagem de teste
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Definir o remetente com tratamento de valores nulos
            String fromAddress = config.getEmailRemetente();
            String fromName = config.getNomeRemetente();

            if (fromAddress != null && !fromAddress.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    try {
                        helper.setFrom(fromAddress, fromName);
                    } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
                        log.error("Erro ao definir remetente com nome: {}", e.getMessage());
                        helper.setFrom(fromAddress);
                    }
                } else {
                    helper.setFrom(fromAddress);
                }
            } else {
                // Fallback para um endereço padrão ou obter do sistema
                String fallbackFrom = "noreply@system.local";
                helper.setFrom(fallbackFrom);
            }

            helper.setTo(emailDestino);
            helper.setSubject("Teste de Configuração SMTP");

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #28a745; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                        .content { background-color: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                        .success-icon { font-size: 48px; text-align: center; margin: 20px 0; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>✓ Teste de Configuração SMTP</h2>
                        </div>
                        <div class="content">
                            <div class="success-icon">✅</div>
                            <p><strong>Parabéns!</strong></p>
                            <p>Se você está lendo este e-mail, significa que suas configurações SMTP estão funcionando corretamente.</p>
                            <p><strong>Detalhes da configuração:</strong></p>
                            <ul>
                                <li><strong>Servidor:</strong> %s</li>
                                <li><strong>Porta:</strong> %d</li>
                                <li><strong>Segurança:</strong> %s</li>
                                <li><strong>Remetente:</strong> %s</li>
                            </ul>
                            <p>Este é um e-mail de teste automático do sistema.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                    config.getServidor(),
                    config.getPorta(),
                    config.getSeguranca().toUpperCase(),
                    config.getEmailRemetente()
                );

            helper.setText(htmlContent, true);

            // Enviar
            mailSender.send(message);

            log.info("E-mail de teste enviado com sucesso para: {}", emailDestino);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("mensagem", "E-mail de teste enviado com sucesso!");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de teste", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("mensagem", "Erro ao enviar e-mail: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
package com.baronesa.emporio.service;

import com.baronesa.emporio.service.smtp.SmtpConfigService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Implementação do serviço de email com configurações dinâmicas de SMTP
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Value("${app.mail.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${app.name:Sistema de Bares}")
    private String appName;

    @Value("${app.company.name:SmartData}")
    private String companyName;

    @Value("${spring.mail.username:no-reply@example.com}")
    private String fallbackMailFrom;

    @Autowired
    private SmtpConfigService smtpConfigService;

    @Override
    @Async
    public void sendEmail(String to, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email desabilitado. Simulação de envio para: {} - Assunto: {}", to, subject);
            log.debug("Corpo: {}", body);
            return;
        }

        try {
            sendMimeMessage(to, subject, body, false, null, null);
            log.info("Email enviado para: {} - Assunto: {}", to, subject);
        } catch (MessagingException ex) {
            log.error("Falha ao enviar email para {} - Assunto: {}", to, subject, ex);
        }
    }

    @Override
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        if (!emailEnabled) {
            log.info("Email HTML desabilitado. Simulação de envio para: {} - Assunto: {}", to, subject);
            return;
        }

        try {
            sendMimeMessage(to, subject, htmlBody, true, null, null);
            log.info("Email HTML enviado para: {} - Assunto: {}", to, subject);
        } catch (MessagingException ex) {
            log.error("Falha ao enviar email HTML para {} - Assunto: {}", to, subject, ex);
        }
    }

    @Override
    public void sendWelcomeEmail(String to, String userName, String temporaryPassword) {
        String subject = String.format("Bem-vindo ao %s!", appName);

        String htmlContent = buildWelcomeEmailContent(userName, temporaryPassword);

        log.info("Enviando email de boas-vindas para: {} - Nome: {}", to, userName);
        log.info("Senha temporária gerada: {}", temporaryPassword);

        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendVerificationEmail(String to, String userName, String verificationLink) {
        String subject = String.format("Verifique seu email - %s", appName);

        log.info("Enviando email de verificação para: {} - Nome: {}", to, userName);
        log.info("Link de verificação: {}", verificationLink);

        String htmlContent = buildVerificationEmailContent(userName, verificationLink);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String to, String userName, String resetLink) {
        String subject = String.format("Redefinição de senha - %s", appName);

        log.info("Enviando email de reset de senha para: {} - Nome: {}", to, userName);
        log.info("Link de reset: {}", resetLink);

        String htmlContent = buildPasswordResetEmailContent(userName, resetLink);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    public void sendNewPasswordEmail(String to, String userName, String newPassword) {
        String subject = String.format("Sua nova senha - %s", appName);

        log.info("Enviando email com nova senha para: {} - Nome: {}", to, userName);
        log.info("Nova senha: {}", newPassword);

        String htmlContent = buildNewPasswordEmailContent(userName, newPassword);
        sendHtmlEmail(to, subject, htmlContent);
    }

    @Override
    @Async
    public void sendComprovanteVenda(Long vendaId, String emailDestinatario, String tipoComprovante) {
        log.warn("Método sendComprovanteVenda não implementado para sistema de bares");
        log.info("VendaId: {}, Email: {}, Tipo: {}", vendaId, emailDestinatario, tipoComprovante);
        // TODO: Implementar quando o módulo de vendas for adicionado
    }

    @Override
    @Async
    public void sendPdf(String to, String subject, String message, byte[] pdfBytes, String fileName) {
        if (!emailEnabled) {
            log.info("Email (PDF) desabilitado. Simulação de envio para: {} - Assunto: {} - Anexo: {}", to, subject, fileName);
            return;
        }

        try {
            sendMimeMessage(to, subject, message, false, pdfBytes, fileName);
            log.info("Email com PDF enviado para: {} - Assunto: {} - Anexo: {} ({} bytes)", to, subject, fileName, pdfBytes != null ? pdfBytes.length : 0);
        } catch (MessagingException ex) {
            log.error("Falha ao enviar email (PDF) para {} - Assunto: {}", to, subject, ex);
        }
    }

    private void sendMimeMessage(String to,
                                 String subject,
                                 String body,
                                 boolean html,
                                 byte[] attachment,
                                 String attachmentName) throws MessagingException {
        // Usar o JavaMailSender com as configurações dinâmicas
        JavaMailSender mailSender = smtpConfigService.createMailSender();
        
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, attachment != null);
        helper.setTo(to);
        
        // Obter o email e nome do remetente das configurações dinâmicas
        var smtpConfig = smtpConfigService.getConfig();
        String fromAddress = smtpConfig.getEmailRemetente();
        String fromName = smtpConfig.getNomeRemetente();
        
        if (fromAddress != null && !fromAddress.isBlank()) {
            if (fromName != null && !fromName.isBlank()) {
                try {
                    helper.setFrom(fromAddress, fromName);
                } catch (jakarta.mail.MessagingException e) {
                    log.error("Erro ao definir remetente com nome: {}", e.getMessage());
                    helper.setFrom(fromAddress);
                } catch (java.io.UnsupportedEncodingException e) {
                    log.error("Codificação de caractere não suportada ao definir remetente: {}", e.getMessage());
                    helper.setFrom(fromAddress);
                }
            } else {
                helper.setFrom(fromAddress);
            }
        } else {
            // Fallback para o valor antigo do application.properties
            helper.setFrom(fallbackMailFrom);
        }
        
        helper.setSubject(subject);
        helper.setText(body != null ? body : "", html);

        if (attachment != null && attachmentName != null) {
            helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
        }

        mailSender.send(mimeMessage);
    }

    // ===============================================================
    // Métodos privados para construir conteúdo HTML dos emails
    // ===============================================================

    private String buildWelcomeEmailContent(String userName, String temporaryPassword) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f5f5f5; padding: 30px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #1976d2;
                              color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .password-box { background-color: #fff; padding: 15px; border: 1px solid #ddd;
                                   border-radius: 4px; font-family: monospace; font-size: 18px;
                                   text-align: center; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Bem-vindo ao %s!</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>
                        <p>Sua conta foi criada com sucesso em nosso sistema.</p>

                        <h3>Suas credenciais de acesso:</h3>
                        <p><strong>Email:</strong> Este mesmo email</p>
                        <p><strong>Senha temporária:</strong></p>
                        <div class="password-box">%s</div>

                        <p style="color: #d32f2f;"><strong>IMPORTANTE:</strong> Por segurança, altere sua senha no primeiro acesso.</p>

                        <center>
                            <a href="%s" class="button">Acessar o Sistema</a>
                        </center>

                        <p>Se você não solicitou esta conta, por favor ignore este email.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 %s. Todos os direitos reservados.</p>
                        <p>Este é um email automático, por favor não responda.</p>
                    </div>
                </div>
            </body>
            </html>
            """, appName, userName, temporaryPassword, getLoginUrl(), companyName);
    }

    private String buildVerificationEmailContent(String userName, String verificationLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f5f5f5; padding: 30px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #4caf50;
                              color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Verificação de Email</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>
                        <p>Obrigado por se cadastrar em nosso sistema!</p>
                        <p>Para completar seu cadastro e ativar sua conta, por favor clique no botão abaixo:</p>

                        <center>
                            <a href="%s" class="button">Verificar Email</a>
                        </center>

                        <p>Ou copie e cole este link em seu navegador:</p>
                        <p style="word-break: break-all; background: #fff; padding: 10px; border: 1px solid #ddd;">%s</p>

                        <p><strong>Este link expira em 24 horas.</strong></p>

                        <p>Se você não criou uma conta em nosso sistema, por favor ignore este email.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 %s. Todos os direitos reservados.</p>
                        <p>Este é um email automático, por favor não responda.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, verificationLink, verificationLink, companyName);
    }

    private String buildPasswordResetEmailContent(String userName, String resetLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #ff9800; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f5f5f5; padding: 30px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #ff9800;
                              color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Redefinição de Senha</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>
                        <p>Recebemos uma solicitação para redefinir a senha da sua conta.</p>
                        <p>Se você fez esta solicitação, clique no botão abaixo para criar uma nova senha:</p>

                        <center>
                            <a href="%s" class="button">Redefinir Senha</a>
                        </center>

                        <p>Ou copie e cole este link em seu navegador:</p>
                        <p style="word-break: break-all; background: #fff; padding: 10px; border: 1px solid #ddd;">%s</p>

                        <p><strong>Este link expira em 2 horas.</strong></p>

                        <p>Se você não solicitou a redefinição de senha, pode ignorar este email com segurança.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 %s. Todos os direitos reservados.</p>
                        <p>Este é um email automático, por favor não responda.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, resetLink, resetLink, companyName);
    }

    private String buildNewPasswordEmailContent(String userName, String newPassword) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #f44336; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f5f5f5; padding: 30px; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #1976d2;
                              color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                    .password-box { background-color: #fff3cd; padding: 15px; border: 1px solid #ffeaa7;
                                   border-radius: 4px; font-family: monospace; font-size: 18px;
                                   text-align: center; margin: 20px 0; }
                    .alert { background-color: #ffebee; border: 1px solid #ffcdd2; padding: 15px;
                            border-radius: 4px; color: #c62828; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Senha Redefinida</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>

                        <div class="alert">
                            <strong>⚠️ ATENÇÃO:</strong> Sua senha foi redefinida pelo administrador do sistema.
                        </div>

                        <p>Uma nova senha temporária foi gerada para sua conta:</p>

                        <div class="password-box">%s</div>

                        <p><strong>Por segurança, você DEVE alterar esta senha no próximo acesso.</strong></p>

                        <center>
                            <a href="%s" class="button">Acessar o Sistema</a>
                        </center>

                        <p>Se você não reconhece esta ação, entre em contato imediatamente com o suporte.</p>
                    </div>
                    <div class="footer">
                        <p>© 2024 %s. Todos os direitos reservados.</p>
                        <p>Este é um email automático, por favor não responda.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, newPassword, getLoginUrl(), companyName);
    }

    private String getLoginUrl() {
        return frontendUrl + "/#/login";
    }
}
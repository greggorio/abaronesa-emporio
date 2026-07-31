package com.baronesa.emporio.service;

// import com.baronesa.emporio.entity.Venda; // COMENTADO - Não aplicável ao sistema de bares

/**
 * Interface para o serviço de email
 * Adicione este método à interface EmailService existente
 */
public interface EmailService {

    /**
     * Envia email simples (texto)
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Envia email HTML
     */
    void sendHtmlEmail(String to, String subject, String htmlBody);

    /**
     * Envia email de confirmação de venda
     * COMENTADO - Não aplicável ao sistema de bares (requer entidade Venda)
     */
    // void sendVendaConfirmacaoEmail(Venda venda);

    /**
     * Envia email de boas-vindas
     */
    void sendWelcomeEmail(String to, String userName, String temporaryPassword);

    /**
     * Envia email de verificação
     */
    void sendVerificationEmail(String to, String userName, String verificationLink);

    /**
     * Envia email de reset de senha
     */
    void sendPasswordResetEmail(String to, String userName, String resetLink);

    /**
     * Envia email com nova senha
     */
    void sendNewPasswordEmail(String to, String userName, String newPassword);

    /**
     * Envia comprovante de venda por email
     * @param vendaId ID da venda
     * @param emailDestinatario Email do destinatário
     * @param tipoComprovante FISCAL ou NAO_FISCAL
     */
    void sendComprovanteVenda(Long vendaId, String emailDestinatario, String tipoComprovante);

    /**
     * Envia um PDF como anexo.
     */
    void sendPdf(String to, String subject, String message, byte[] pdfBytes, String fileName);
}

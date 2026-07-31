package com.baronesa.emporio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.service.payment.PaymentStatusUpdater;
import com.baronesa.emporio.service.payment.mapper.MercadoPagoStatusMapper;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoWebhookService {

    private final ConfigManager configManager;
    private final MercadoPagoService mercadoPagoService;
    private final ObjectMapper objectMapper;
    private final PaymentStatusUpdater paymentStatusUpdater;
    private final MercadoPagoStatusMapper mercadoPagoStatusMapper;
    private final List<WebhookLogEntry> webhookLogs = new CopyOnWriteArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1L);

    /**
     * Valida a assinatura do webhook
     */
    public boolean validarAssinatura(Map<String, Object> payload, String signature, String requestId) {
        try {
            // Obter secret do webhook
            String webhookSecret = configManager.getConfig("mercadopago_webhook_secret", "");

            if (webhookSecret.isEmpty()) {
                log.warn("Webhook secret não configurado - aceitando webhook sem validação");
                return true;
            }

            if (signature == null || signature.isEmpty()) {
                log.error("Assinatura não fornecida no webhook");
                return false;
            }

            // Formato da assinatura: ts=timestamp,v1=hash
            String[] parts = signature.split(",");
            String timestamp = null;
            String hash = null;

            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2) {
                    if ("ts".equals(keyValue[0])) {
                        timestamp = keyValue[1];
                    } else if ("v1".equals(keyValue[0])) {
                        hash = keyValue[1];
                    }
                }
            }

            if (timestamp == null || hash == null) {
                log.error("Formato de assinatura inválido");
                return false;
            }

            // Criar string para assinar: id;request-id;timestamp
            String dataToSign = String.format("id:%s;request-id:%s;ts:%s;",
                    payload.get("id"),
                    requestId,
                    timestamp);

            // Calcular HMAC
            String calculatedHash = calculateHMAC(dataToSign, webhookSecret);

            // Comparar hashes
            boolean valid = hash.equals(calculatedHash);

            if (!valid) {
                log.error("Assinatura inválida. Esperado: {}, Recebido: {}", calculatedHash, hash);
            }

            return valid;

        } catch (Exception e) {
            log.error("Erro ao validar assinatura do webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Processa o webhook de forma assíncrona
     */
    @Async
    public void processarWebhookAsync(Map<String, Object> payload, String requestId) {
        WebhookLogEntry webhookLog = null;

        try {
            // 1. Salvar log do webhook
            webhookLog = salvarWebhookLog(payload, requestId);

            // 2. Extrair informações do payload
            String tipo = (String) payload.get("type");
            String acao = (String) payload.get("action");
            Map<String, Object> data = (Map<String, Object>) payload.get("data");

            log.info("Processando webhook - Tipo: {}, Ação: {}", tipo, acao);

            // 3. Processar baseado no tipo
            if ("payment".equals(tipo)) {
                processarWebhookPagamento(data, acao, webhookLog);
            } else if ("merchant_order".equals(tipo)) {
                processarWebhookPedido(data, acao, webhookLog);
            } else {
                log.info("Tipo de webhook não processado: {}", tipo);
                webhookLog.setStatus("IGNORADO");
                webhookLog.setMensagemProcessamento("Tipo não processado: " + tipo);
            }

            // 4. Atualizar log como processado
            webhookLog.setDataProcessamento(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Erro ao processar webhook: {}", e.getMessage(), e);

            if (webhookLog != null) {
                webhookLog.setStatus("ERRO");
                webhookLog.setMensagemProcessamento("Erro: " + e.getMessage());
                webhookLog.setDataProcessamento(LocalDateTime.now());
            }
        }
    }

    /**
     * Processa webhook de pagamento
     */
    private void processarWebhookPagamento(Map<String, Object> data, String acao, WebhookLogEntry webhookLog) {
        try {
            String paymentId = String.valueOf(data.get("id"));

            log.info("Processando webhook de pagamento - ID: {}, Ação: {}", paymentId, acao);

            // Opcional: em ambiente de teste, permitir usar status vindo no payload sem consultar o MP.
            boolean allowPayloadStatus = configManager.getBooleanConfig("mercadopago_webhook_allow_payload_status", false);
            String payloadStatus = data.get("status") != null ? String.valueOf(data.get("status")) : null;
            if (allowPayloadStatus && payloadStatus != null) {
                PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                        paymentId,
                        payloadStatus,
                        objectMapper.writeValueAsString(data)
                );
                paymentStatusUpdater.onPaymentStatusUpdated(update);
                webhookLog.setStatus("PROCESSADO");
                webhookLog.setMensagemProcessamento(String.format("Pagamento (payload) atualizado - Status: %s", payloadStatus));
                return;
            }

            // Consultar detalhes do pagamento
            var paymentDetails = mercadoPagoService.consultarPagamento(paymentId);

            String status = paymentDetails.path("status").asText();
            String statusDetail = paymentDetails.path("status_detail").asText();

            log.info("Status do pagamento: {}, Detalhe: {}", status, statusDetail);

            // Notificar desacoplado para posterior integração com domínio de vendas
            PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                    paymentId,
                    status,
                    objectMapper.writeValueAsString(paymentDetails)
            );
            paymentStatusUpdater.onPaymentStatusUpdated(update);

            webhookLog.setStatus("PROCESSADO");
            webhookLog.setMensagemProcessamento(
                    String.format("Pagamento atualizado - Status: %s", status)
            );

        } catch (Exception e) {
            log.error("Erro ao processar webhook de pagamento: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar pagamento", e);
        }
    }

    /**
     * Processa webhook de pedido (merchant order)
     */
    private void processarWebhookPedido(Map<String, Object> data, String acao, WebhookLogEntry webhookLog) {
        try {
            String orderId = String.valueOf(data.get("id"));

            log.info("Processando webhook de pedido - ID: {}, Ação: {}", orderId, acao);

            // Por enquanto, apenas registrar
            webhookLog.setStatus("PROCESSADO");
            webhookLog.setMensagemProcessamento("Pedido registrado");

        } catch (Exception e) {
            log.error("Erro ao processar webhook de pedido: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar pedido", e);
        }
    }

    /**
     * Salva log do webhook
     */
    private WebhookLogEntry salvarWebhookLog(Map<String, Object> payload, String requestId) {
        try {
            WebhookLogEntry log = new WebhookLogEntry();
            log.setId(idSequence.getAndIncrement());
            log.setProvider("MERCADOPAGO");
            log.setRequestId(requestId);
            log.setTipo((String) payload.get("type"));
            log.setAcao((String) payload.get("action"));
            log.setPayload(objectMapper.writeValueAsString(payload));
            log.setDataRecebimento(LocalDateTime.now());
            log.setStatus("RECEBIDO");
            log.setTentativas(0);

            webhookLogs.add(log);
            return log;

        } catch (Exception e) {
            log.error("Erro ao salvar log do webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar log", e);
        }
    }

    /**
     * Reprocessa um webhook específico
     */
    public boolean reprocessarWebhook(Long webhookLogId) {
        try {
            var logOpt = webhookLogs.stream()
                    .filter(it -> it.getId().equals(webhookLogId))
                    .findFirst();

            if (logOpt.isEmpty()) {
                return false;
            }

            var log = logOpt.get();
            var payload = objectMapper.readValue(log.getPayload(), Map.class);

            // Resetar status
            log.setStatus("REPROCESSANDO");
            log.setTentativas(log.getTentativas() + 1);

            // Reprocessar
            processarWebhookAsync(payload, log.getRequestId());

            return true;

        } catch (Exception e) {
            log.error("Erro ao reprocessar webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Lista logs de webhooks
     */
    public Page<WebhookLogEntry> listarWebhookLogs(int page, int size, String status) {
        PageRequest pageRequest = PageRequest.of(page, size);

        List<WebhookLogEntry> filtered = new ArrayList<>(webhookLogs);
        if (status != null && !status.isEmpty()) {
            filtered = filtered.stream()
                    .filter(log -> status.equalsIgnoreCase(log.getStatus()))
                    .toList();
        }

        int start = Math.min((int) pageRequest.getOffset(), filtered.size());
        int end = Math.min(start + pageRequest.getPageSize(), filtered.size());
        List<WebhookLogEntry> slice = filtered.subList(start, end);

        return new PageImpl<>(slice, pageRequest, filtered.size());
    }

    /**
     * Verifica se webhook está configurado
     */
    public boolean isWebhookConfigurado() {
        String webhookUrl = configManager.getConfig("mercadopago_webhook_url", "");
        String webhookSecret = configManager.getConfig("mercadopago_webhook_secret", "");

        return !webhookUrl.isEmpty() || !webhookSecret.isEmpty();
    }

    /**
     * Calcula HMAC-SHA256
     */
    private String calculateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Converter para hex
            StringBuilder result = new StringBuilder();
            for (byte b : hmacBytes) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular HMAC", e);
        }
    }

    /**
     * Estrutura simples para armazenar logs em memória enquanto não conectamos ao banco.
     */
    public static class WebhookLogEntry {
        private Long id;
        private String provider;
        private String requestId;
        private String tipo;
        private String acao;
        private String payload;
        private LocalDateTime dataRecebimento;
        private LocalDateTime dataProcessamento;
        private String status;
        private String mensagemProcessamento;
        private Integer tentativas;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getAcao() { return acao; }
        public void setAcao(String acao) { this.acao = acao; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public LocalDateTime getDataRecebimento() { return dataRecebimento; }
        public void setDataRecebimento(LocalDateTime dataRecebimento) { this.dataRecebimento = dataRecebimento; }
        public LocalDateTime getDataProcessamento() { return dataProcessamento; }
        public void setDataProcessamento(LocalDateTime dataProcessamento) { this.dataProcessamento = dataProcessamento; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMensagemProcessamento() { return mensagemProcessamento; }
        public void setMensagemProcessamento(String mensagemProcessamento) { this.mensagemProcessamento = mensagemProcessamento; }
        public Integer getTentativas() { return tentativas; }
        public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }
    }
}

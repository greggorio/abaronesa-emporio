package com.baronesa.emporio.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.baronesa.emporio.entity.MPPayment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.config.MPConfig;
import com.baronesa.emporio.dto.MPCreatePaymentDTO;
import com.baronesa.emporio.dto.request.MPPaymentRequest;
import com.baronesa.emporio.dto.request.MPSearchPaymentRequest;
import com.baronesa.emporio.dto.response.MPPaymentResponse;
import com.baronesa.emporio.dto.response.MPSearchPaymentResponse;
import com.baronesa.emporio.service.payment.PaymentStatusUpdater;
import com.baronesa.emporio.service.payment.mapper.MercadoPagoStatusMapper;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MPPaymentService {

    private final MPConfig mpConfig;
    private final ObjectMapper objectMapper;
    private final PaymentStatusUpdater paymentStatusUpdater;
    private final MercadoPagoStatusMapper mercadoPagoStatusMapper;

    private final RestTemplate restTemplate;

    /**
     * ⭐ MÉTODO PRINCIPAL COM RETRY IMPLEMENTADO
     * Cria um novo pagamento no Mercado Pago com retry automático para erros 500
     */
    @Retryable(
            value = {HttpServerErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public MPPaymentResponse createPayment(MPPaymentRequest request) {
        try {
            log.info("Criando pagamento no Mercado Pago para external_reference: {}", request.getExternalReference());

            // Validações básicas
            validatePaymentRequest(request);

            // LOG DETALHADO DO REQUEST RECEBIDO
            logRequestDetails(request);

            // CONVERTER para DTO limpo
            MPCreatePaymentDTO cleanDto = convertToCleanDTO(request);

            // LOG DO DTO LIMPO QUE VAI PARA O MP
            logCleanDtoDetails(cleanDto);

            String url = mpConfig.getApiUrl() + "/v1/payments";
            log.info("🌐 URL: {}", url);

            HttpHeaders headers = createHeaders();
            HttpEntity<MPCreatePaymentDTO> entity = new HttpEntity<>(cleanDto, headers);

            ResponseEntity<MPPayment> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MPPayment.class
            );

            MPPayment payment = response.getBody();
            log.info("Pagamento criado com sucesso. ID: {}, Status: {}", payment.getId(), payment.getStatus());
            notifyDeliveryIfApproved(payment);

            // Verificar se o pagamento foi rejeitado e incluir mensagem personalizada
            MPPaymentResponse.MPPaymentResponseBuilder responseBuilder = MPPaymentResponse.builder()
                    .payment(payment)
                    .statusCode(response.getStatusCode().value());

            if ("rejected".equals(payment.getStatus())) {
                String rejectMessage = getRejectMessage(payment.getStatusDetail());
                log.warn("⚠️ Pagamento rejeitado - Status Detail: {}, Mensagem: {}",
                        payment.getStatusDetail(), rejectMessage);
                responseBuilder.status("rejected").message(rejectMessage);
            } else {
                responseBuilder.status("success").message("Pagamento processado com sucesso!");
            }

            return responseBuilder.build();

        } catch (HttpServerErrorException e) {
            // ⭐ TRATAMENTO ESPECÍFICO PARA ERROS 500 - SERÁ RETENTADO AUTOMATICAMENTE
            log.warn("⚠️ Erro 500 do Mercado Pago (tentativa será repetida): Status={}, Body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            log.error("❗ Corpo do erro do Mercado Pago: {}", e.getResponseBodyAsString());
            throw e; // Re-throw para triggerar o retry

        } catch (HttpClientErrorException e) {
            // ⭐ ERROS 4XX NÃO SÃO RETENTADOS
            log.error("❌ Erro HTTP ao criar pagamento: Status={}, Body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return handleErrorResponse(e);

        } catch (IllegalArgumentException e) {
            log.error("❌ Erro de validação: {}", e.getMessage());
            return MPPaymentResponse.builder()
                    .status("validation_error")
                    .statusCode(400)
                    .message(e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("❌ Erro inesperado ao criar pagamento", e);
            return MPPaymentResponse.builder()
                    .status("internal_error")
                    .statusCode(500)
                    .message("Erro interno do servidor. Tente novamente em alguns instantes.")
                    .build();
        }
    }

    private void notifyDeliveryIfApproved(MPPayment payment) {
        if (payment == null || payment.getStatus() == null) return;
        if (!"approved".equalsIgnoreCase(payment.getStatus())) return;
        try {
            String rawPayload = objectMapper.writeValueAsString(payment);
            PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                    String.valueOf(payment.getId()),
                    payment.getStatus(),
                    rawPayload
            );
            paymentStatusUpdater.onPaymentStatusUpdated(update);
        } catch (Exception e) {
            log.warn("Falha ao notificar delivery status imediato pagamentoId={}", payment.getId(), e);
        }
    }

    /**
     * ⭐ MÉTODO DE RECUPERAÇÃO - CHAMADO QUANDO TODAS AS TENTATIVAS FALHARAM
     */
    @Recover
    public MPPaymentResponse recoverFromServerError(HttpServerErrorException ex, MPPaymentRequest request) {
        log.error("❌ Todas as tentativas de pagamento falharam para external_reference: {}. Erro final: {}",
                request.getExternalReference(), ex.getMessage());
        log.error("❗ Corpo do erro final do Mercado Pago: {}", ex.getResponseBodyAsString());

        // ⭐ TRATAMENTO ESPECÍFICO PARA DIFERENTES TIPOS DE ERRO 500
        String errorBody = ex.getResponseBodyAsString();
        String friendlyMessage;

        if (errorBody.contains("internal_error")) {
            friendlyMessage = "Sistema de pagamento temporariamente instável. Aguarde alguns minutos e tente novamente.";
        } else if (errorBody.contains("timeout")) {
            friendlyMessage = "Timeout na comunicação com o sistema de pagamento. Tente novamente em alguns instantes.";
        } else {
            friendlyMessage = "Erro temporário do sistema de pagamento. Aguarde alguns minutos antes de tentar novamente.";
        }

        return MPPaymentResponse.builder()
                .status("temporary_error")
                .statusCode(ex.getStatusCode().value())
                .message(friendlyMessage)
                .build();
    }

    /**
     * Valida o request de pagamento
     */
    private void validatePaymentRequest(MPPaymentRequest request) {
        if (request.getTransactionAmount() == null) {
            throw new IllegalArgumentException("O valor da transação é obrigatório");
        }

        if (request.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da transação deve ser maior que zero");
        }

        if (request.getPaymentMethodId() == null || request.getPaymentMethodId().trim().isEmpty()) {
            throw new IllegalArgumentException("O método de pagamento é obrigatório");
        }

        if (request.getPayer() == null || request.getPayer().getEmail() == null) {
            throw new IllegalArgumentException("Os dados do pagador são obrigatórios");
        }

        // Validação específica para cartão de crédito
        if ("credit_card".equals(request.getPaymentMethodId()) &&
                (request.getToken() == null || request.getToken().trim().isEmpty())) {
            throw new IllegalArgumentException("Token do cartão é obrigatório para pagamento com cartão de crédito");
        }
    }

    /**
     * Retorna mensagem de rejeição baseada no status detail do Mercado Pago
     */
    private String getRejectMessage(String statusDetail) {
        if (statusDetail == null) {
            return "Pagamento recusado. Tente outro cartão ou meio de pagamento.";
        }

        switch (statusDetail) {
            case "cc_rejected_high_risk":
                return "Pagamento recusado por medidas de segurança. Tente outro cartão ou meio de pagamento.";
            case "cc_rejected_insufficient_amount":
                return "Saldo ou limite insuficiente no cartão. Verifique com seu banco.";
            case "cc_rejected_bad_filled_card_number":
                return "Número do cartão incorreto. Verifique os dados informados.";
            case "cc_rejected_bad_filled_security_code":
                return "Código de segurança (CVV) incorreto. Verifique os dados do cartão.";
            case "cc_rejected_bad_filled_date":
                return "Data de validade incorreta. Verifique os dados do cartão.";
            case "cc_rejected_card_disabled":
                return "Cartão desabilitado ou bloqueado. Entre em contato com seu banco.";
            case "cc_rejected_call_for_authorize":
                return "Autorização necessária. Entre em contato com seu banco para liberar a transação.";
            case "cc_rejected_card_type_not_allowed":
                return "Tipo de cartão não aceito. Tente com outro cartão.";
            case "cc_rejected_max_attempts":
                return "Número máximo de tentativas excedido. Aguarde alguns minutos antes de tentar novamente.";
            case "cc_rejected_duplicated_payment":
                return "Pagamento duplicado detectado. Verifique se a transação não foi processada anteriormente.";
            case "cc_rejected_invalid_installments":
                return "Número de parcelas inválido para este cartão.";
            case "cc_rejected_blacklist":
                return "Pagamento recusado. Entre em contato com seu banco.";
            case "cc_rejected_other_reason":
                return "Pagamento recusado pelo banco emissor. Tente outro cartão ou meio de pagamento.";
            default:
                return "Pagamento recusado. Tente outro cartão ou meio de pagamento.";
        }
    }

    /**
     * Traduz erros HTTP do Mercado Pago em mensagens amigáveis
     */
    private String translateMercadoPagoError(String errorBody, HttpStatus statusCode) {
        try {
            JsonNode errorNode = objectMapper.readTree(errorBody);

            // Verifica se há uma mensagem específica
            if (errorNode.has("message")) {
                String message = errorNode.get("message").asText();

                // Traduzir mensagens específicas conhecidas
                switch (message.toLowerCase()) {
                    case "invalid parameter":
                        return "Dados inválidos fornecidos. Verifique as informações do pagamento.";
                    case "bin not found":
                        return "Cartão não reconhecido. Verifique o número do cartão.";
                    case "invalid payment method":
                        return "Método de pagamento inválido.";
                    case "invalid card token":
                        return "Token do cartão inválido. Tente inserir os dados novamente.";
                    default:
                        break;
                }
            }

            // Verifica se há erros específicos na estrutura "cause"
            if (errorNode.has("cause")) {
                JsonNode causeNode = errorNode.get("cause");
                if (causeNode.isArray() && causeNode.size() > 0) {
                    JsonNode firstCause = causeNode.get(0);
                    if (firstCause.has("code")) {
                        String code = firstCause.get("code").asText();
                        return translateErrorCode(code);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Erro ao interpretar resposta de erro do Mercado Pago: {}", e.getMessage());
        }

        // ⭐ MENSAGENS MELHORADAS BASEADAS NO STATUS HTTP
        switch (statusCode) {
            case BAD_REQUEST:
                return "Dados inválidos fornecidos. Verifique as informações e tente novamente.";
            case UNAUTHORIZED:
                return "Erro de autenticação. Tente novamente em alguns instantes.";
            case FORBIDDEN:
                return "Operação não permitida. Entre em contato com o suporte.";
            case NOT_FOUND:
                return "Recurso não encontrado.";
            case UNPROCESSABLE_ENTITY:
                return "Não foi possível processar o pagamento. Verifique os dados informados.";
            case TOO_MANY_REQUESTS:
                return "Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.";
            case INTERNAL_SERVER_ERROR:
                return "Sistema de pagamento temporariamente instável. Aguarde alguns minutos e tente novamente.";
            case BAD_GATEWAY:
            case SERVICE_UNAVAILABLE:
            case GATEWAY_TIMEOUT:
                return "Sistema de pagamento temporariamente indisponível. Tente novamente em alguns instantes.";
            default:
                return "Erro ao processar pagamento. Tente novamente ou entre em contato com o suporte.";
        }
    }

    /**
     * Traduz códigos específicos de erro do Mercado Pago
     */
    private String translateErrorCode(String code) {
        switch (code) {
            case "10105":
                return "Cartão não encontrado ou inválido. Verifique os dados do cartão.";
            case "10106":
                return "Número de parcelas inválido.";
            case "3034":
                return "Token do cartão inválido ou expirado. Tente inserir os dados novamente.";
            case "2067":
                return "Valor mínimo de transação não atendido.";
            case "2081":
                return "Parâmetros de pagamento inválidos.";
            default:
                return "Erro na validação dos dados. Verifique as informações fornecidas.";
        }
    }

    // Logs detalhados (métodos auxiliares)
    private void logRequestDetails(MPPaymentRequest request) {
        log.info("📥 Request recebido:");
        log.info("  - External Reference: {}", request.getExternalReference());
        log.info("  - Transaction Amount: {}", request.getTransactionAmount());
        log.info("  - Payment Method ID: {}", request.getPaymentMethodId());
        log.info("  - Token: {}", request.getToken() != null ? "PRESENTE" : "AUSENTE");
        log.info("  - Installments: {}", request.getInstallments());
        log.info("  - Description: {}", request.getDescription());

        if (request.getPayer() != null) {
            log.info("  - Payer Email: {}", request.getPayer().getEmail());
            log.info("  - Payer First Name: {}", request.getPayer().getFirstName());
            log.info("  - Payer Last Name: {}", request.getPayer().getLastName());

            if (request.getPayer().getIdentification() != null) {
                log.info("  - Payer ID Type: {}", request.getPayer().getIdentification().getType());
                log.info("  - Payer ID Number: {}", request.getPayer().getIdentification().getNumber());
            }
        }
    }

    private void logCleanDtoDetails(MPCreatePaymentDTO cleanDto) {
        log.info("📤 DTO para Mercado Pago:");
        log.info("  - External Reference: {}", cleanDto.getExternalReference());
        log.info("  - Transaction Amount: {}", cleanDto.getTransactionAmount());
        log.info("  - Payment Method ID: {}", cleanDto.getPaymentMethodId());
        log.info("  - Token: {}", cleanDto.getToken());
        log.info("  - Installments: {}", cleanDto.getInstallments());
        log.info("  - Description: {}", cleanDto.getDescription());
        log.info("  - Capture: {}", cleanDto.getCapture());
        log.info("  - Binary Mode: {}", cleanDto.getBinaryMode());

        if (cleanDto.getPayer() != null) {
            log.info("  - Payer Email: {}", cleanDto.getPayer().getEmail());
            log.info("  - Payer First Name: {}", cleanDto.getPayer().getFirstName());
            log.info("  - Payer Last Name: {}", cleanDto.getPayer().getLastName());
            log.info("  - Payer Entity Type: {}", cleanDto.getPayer().getEntityType());
            log.info("  - Payer Type: {}", cleanDto.getPayer().getType());
        }
    }

    // Método para converter para DTO limpo (mantido igual)
    private MPCreatePaymentDTO convertToCleanDTO(MPPaymentRequest request) {
        MPCreatePaymentDTO.MPPayerDTO payerDto = null;

        if (request.getPayer() != null) {
            MPCreatePaymentDTO.MPIdentificationDTO identificationDto = null;

            if (request.getPayer().getIdentification() != null) {
                identificationDto = MPCreatePaymentDTO.MPIdentificationDTO.builder()
                        .type(request.getPayer().getIdentification().getType())
                        .number(request.getPayer().getIdentification().getNumber())
                        .build();
            }

            payerDto = MPCreatePaymentDTO.MPPayerDTO.builder()
                    .email(request.getPayer().getEmail())
                    .firstName(request.getPayer().getFirstName())
                    .lastName(request.getPayer().getLastName())
                    .identification(identificationDto)
                    .entityType("individual")
                    .type("customer")
                    .build();
        }

        return MPCreatePaymentDTO.builder()
                .externalReference(request.getExternalReference())
                .transactionAmount(request.getTransactionAmount())
                .description(request.getDescription())
                .paymentMethodId(request.getPaymentMethodId())
                .token(request.getToken())
                .installments(request.getInstallments())
                .payer(payerDto)
                .capture(true)
                .binaryMode(false)
                .build();
    }

    /**
     * Consulta um pagamento específico por ID
     */
    public MPPaymentResponse getPayment(Long paymentId) {
        try {
            log.info("Consultando pagamento ID: {}", paymentId);

            String url = mpConfig.getApiUrl() + "/v1/payments/" + paymentId;

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<MPPayment> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MPPayment.class
            );

            log.info("Pagamento consultado com sucesso. Status: {}", response.getBody().getStatus());

            return MPPaymentResponse.builder()
                    .payment(response.getBody())
                    .status("success")
                    .statusCode(response.getStatusCode().value())
                    .message("Pagamento consultado com sucesso")
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("Erro ao consultar pagamento ID {}: {}", paymentId, e.getResponseBodyAsString());
            return handleErrorResponse(e);
        } catch (Exception e) {
            log.error("Erro inesperado ao consultar pagamento ID: {}", paymentId, e);
            return MPPaymentResponse.builder()
                    .status("internal_error")
                    .statusCode(500)
                    .message("Erro interno ao consultar pagamento")
                    .build();
        }
    }

    /**
     * Busca pagamentos com filtros
     */
    public MPSearchPaymentResponse searchPayments(MPSearchPaymentRequest request) {
        try {
            log.info("Buscando pagamentos com filtros: external_reference={}, status={}",
                    request.getExternalReference(), request.getStatus());

            String url = buildSearchUrl(request);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<MPSearchPaymentResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MPSearchPaymentResponse.class
            );

            log.info("Busca realizada com sucesso. Total encontrado: {}",
                    response.getBody().getPaging().getTotal());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Erro ao buscar pagamentos: {}", e.getResponseBodyAsString());
            String friendlyMessage = translateMercadoPagoError(e.getResponseBodyAsString(), HttpStatus.valueOf(e.getStatusCode().value()));
            throw new RuntimeException(friendlyMessage);
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar pagamentos", e);
            throw new RuntimeException("Erro interno ao buscar pagamentos");
        }
    }

    /**
     * Cancela um pagamento
     */
    public MPPaymentResponse cancelPayment(Long paymentId) {
        try {
            log.info("Cancelando pagamento ID: {}", paymentId);

            String url = mpConfig.getApiUrl() + "/v1/payments/" + paymentId;

            HttpHeaders headers = createHeaders();

            // Body para cancelamento
            Map<String, String> cancelRequest = Map.of("status", "cancelled");
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(cancelRequest, headers);

            ResponseEntity<MPPayment> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    MPPayment.class
            );

            log.info("Pagamento cancelado com sucesso. Status: {}", response.getBody().getStatus());

            return MPPaymentResponse.builder()
                    .payment(response.getBody())
                    .status("success")
                    .statusCode(response.getStatusCode().value())
                    .message("Pagamento cancelado com sucesso")
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("Erro ao cancelar pagamento ID {}: {}", paymentId, e.getResponseBodyAsString());
            return handleErrorResponse(e);
        } catch (Exception e) {
            log.error("Erro inesperado ao cancelar pagamento ID: {}", paymentId, e);
            return MPPaymentResponse.builder()
                    .status("internal_error")
                    .statusCode(500)
                    .message("Erro interno ao cancelar pagamento")
                    .build();
        }
    }

    /**
     * ⭐ HEADERS MELHORADOS COM ANTI-RATE-LIMITING
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mpConfig.getAccessToken());

        // ⭐ Headers para reduzir rate limiting e identificar requests únicos
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());
        headers.set("X-Request-Id", UUID.randomUUID().toString());
        headers.set("User-Agent", "Loja-Store/1.0");

        return headers;
    }

    private String buildSearchUrl(MPSearchPaymentRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(mpConfig.getApiUrl() + "/v1/payments/search");

        if (request.getExternalReference() != null) {
            builder.queryParam("external_reference", request.getExternalReference());
        }
        if (request.getStatus() != null) {
            builder.queryParam("status", request.getStatus());
        }
        if (request.getPaymentMethodId() != null) {
            builder.queryParam("payment_method_id", request.getPaymentMethodId());
        }
        if (request.getPaymentTypeId() != null) {
            builder.queryParam("payment_type_id", request.getPaymentTypeId());
        }
        if (request.getBeginDate() != null) {
            builder.queryParam("begin_date", request.getBeginDate().toString());
        }
        if (request.getEndDate() != null) {
            builder.queryParam("end_date", request.getEndDate().toString());
        }
        if (request.getLimit() != null) {
            builder.queryParam("limit", request.getLimit());
        }
        if (request.getOffset() != null) {
            builder.queryParam("offset", request.getOffset());
        }
        if (request.getSort() != null) {
            builder.queryParam("sort", request.getSort());
        }
        if (request.getCriteria() != null) {
            builder.queryParam("criteria", request.getCriteria());
        }

        return builder.toUriString();
    }

    /**
     * ⭐ MÉTODO MELHORADO PARA TRATAR RESPOSTAS DE ERRO
     */
    private MPPaymentResponse handleErrorResponse(HttpClientErrorException e) {
        String friendlyMessage = translateMercadoPagoError(e.getResponseBodyAsString(), HttpStatus.valueOf(e.getStatusCode().value()));

        // ⭐ DIFERENTES STATUS BASEADOS NO TIPO DE ERRO
        String status = "error";
        if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            status = "rate_limit_exceeded";
        } else if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            status = "validation_error";
        }

        return MPPaymentResponse.builder()
                .status(status)
                .statusCode(e.getStatusCode().value())
                .message(friendlyMessage)
                .build();
    }
}

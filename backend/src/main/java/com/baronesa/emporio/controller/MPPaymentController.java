package com.baronesa.emporio.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.dto.request.MPPaymentRequest;
import com.baronesa.emporio.dto.request.MPSearchPaymentRequest;
import com.baronesa.emporio.dto.response.MPPaymentResponse;
import com.baronesa.emporio.dto.response.MPSearchPaymentResponse;
import com.baronesa.emporio.service.MPPaymentService;
import com.baronesa.emporio.service.MPWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/mercadopago")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mercado Pago Payments", description = "API para integração com Mercado Pago")
public class MPPaymentController {

    private final MPPaymentService paymentService;
    private final MPWebhookService webhookService;

    @Operation(
            summary = "Criar Pagamento",
            description = "Cria um novo pagamento no Mercado Pago.\n\n" +
                    "**Métodos de pagamento suportados:**\n" +
                    "- Cartão de crédito/débito\n" +
                    "- PIX\n" +
                    "- Boleto bancário\n" +
                    "- Outros métodos disponíveis no MP\n\n" +
                    "**Nota:** Este endpoint está configurado para sandbox (testes).",
            tags = {"Mercado Pago Payments"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Pagamento criado com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MPPaymentResponse.class),
                            examples = @ExampleObject(
                                    name = "Sucesso",
                                    value = "{\n" +
                                            "  \"status\": \"success\",\n" +
                                            "  \"statusCode\": 201,\n" +
                                            "  \"message\": \"Pagamento criado com sucesso\",\n" +
                                            "  \"payment\": {\n" +
                                            "    \"id\": 12345,\n" +
                                            "    \"status\": \"pending\",\n" +
                                            "    \"externalReference\": \"ORDER-123\",\n" +
                                            "    \"transactionAmount\": 100.00\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Erro de validação",
                                    value = "{\n" +
                                            "  \"status\": \"error\",\n" +
                                            "  \"statusCode\": 400,\n" +
                                            "  \"message\": \"Dados inválidos\"\n" +
                                            "}"
                            )
                    )
            )
    })
    @PostMapping("/payments")
    public ResponseEntity<MPPaymentResponse> createPayment(
            @Parameter(description = "Dados do pagamento a ser criado", required = true)
            @Valid @RequestBody MPPaymentRequest request) {

        log.info("Recebida solicitação para criar pagamento: {}", request.getExternalReference());
        MPPaymentResponse response = paymentService.createPayment(request);

        HttpStatus status = HttpStatus.resolve(response.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(response);
    }

    @Operation(
            summary = "Consultar Pagamento",
            description = "Consulta um pagamento específico pelo ID no Mercado Pago",
            tags = {"Mercado Pago Payments"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pagamento encontrado",
                    content = @Content(schema = @Schema(implementation = MPPaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Pagamento não encontrado"
            )
    })
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<MPPaymentResponse> getPayment(
            @Parameter(description = "ID do pagamento no Mercado Pago", example = "12345")
            @PathVariable Long paymentId) {

        log.info("Consultando pagamento ID: {}", paymentId);
        MPPaymentResponse response = paymentService.getPayment(paymentId);

        HttpStatus status = response.getStatusCode() == 200 ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(
            summary = "Buscar Pagamentos",
            description = "Busca pagamentos com filtros específicos.\n\n" +
                    "**Filtros disponíveis:**\n" +
                    "- external_reference: Referência externa do pedido\n" +
                    "- status: Status do pagamento (pending, approved, rejected, etc.)\n" +
                    "- payment_method_id: Método de pagamento (visa, mastercard, pix, etc.)\n" +
                    "- payment_type_id: Tipo de pagamento (credit_card, debit_card, etc.)",
            tags = {"Mercado Pago Payments"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Busca realizada com sucesso",
                    content = @Content(schema = @Schema(implementation = MPSearchPaymentResponse.class))
            )
    })
    @GetMapping("/payments/search")
    public ResponseEntity<MPSearchPaymentResponse> searchPayments(
            @Parameter(description = "Referência externa do pedido", example = "ORDER-123")
            @RequestParam(required = false) String externalReference,

            @Parameter(description = "Status do pagamento", example = "approved")
            @RequestParam(required = false) String status,

            @Parameter(description = "Método de pagamento", example = "visa")
            @RequestParam(required = false) String paymentMethodId,

            @Parameter(description = "Tipo de pagamento", example = "credit_card")
            @RequestParam(required = false) String paymentTypeId,

            @Parameter(description = "Limite de resultados (máximo 50)", example = "10")
            @RequestParam(required = false, defaultValue = "50") Integer limit,

            @Parameter(description = "Offset para paginação", example = "0")
            @RequestParam(required = false, defaultValue = "0") Integer offset) {

        MPSearchPaymentRequest searchRequest = MPSearchPaymentRequest.builder()
                .externalReference(externalReference)
                .status(status)
                .paymentMethodId(paymentMethodId)
                .paymentTypeId(paymentTypeId)
                .limit(limit)
                .offset(offset)
                .sort("date_created")
                .criteria("desc")
                .build();

        log.info("Buscando pagamentos com filtros: external_reference={}, status={}",
                externalReference, status);
        MPSearchPaymentResponse response = paymentService.searchPayments(searchRequest);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cancelar Pagamento",
            description = "Cancela um pagamento específico no Mercado Pago",
            tags = {"Mercado Pago Payments"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pagamento cancelado com sucesso",
                    content = @Content(schema = @Schema(implementation = MPPaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Pagamento não encontrado"
            )
    })
    @PutMapping("/payments/{paymentId}/cancel")
    public ResponseEntity<MPPaymentResponse> cancelPayment(
            @Parameter(description = "ID do pagamento a ser cancelado", example = "12345")
            @PathVariable Long paymentId) {

        log.info("Cancelando pagamento ID: {}", paymentId);
        MPPaymentResponse response = paymentService.cancelPayment(paymentId);

        HttpStatus status = response.getStatusCode() == 200 ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(
            summary = "Webhook do Mercado Pago",
            description = "Endpoint para receber notificações do Mercado Pago sobre mudanças de status de pagamentos.\n\n" +
                    "**Como funciona:**\n" +
                    "1. MP envia POST com dados do evento\n" +
                    "2. Sistema valida assinatura (se configurada)\n" +
                    "3. Processa a notificação\n" +
                    "4. Retorna status 200 para confirmar recebimento",
            tags = {"Mercado Pago Webhooks"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Webhook processado com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Erro no processamento do webhook"
            )
    })
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @Parameter(description = "Corpo da notificação do Mercado Pago")
            @RequestBody String payload,

            HttpServletRequest request) {

        String signature = request.getHeader("x-signature");

        try {
            log.info("Webhook recebido do Mercado Pago");
            webhookService.processWebhook(payload, signature);

            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("status", "success");
            successResponse.put("message", "Webhook processado");

            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            log.error("Erro ao processar webhook: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Erro ao processar webhook");

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @Operation(
            summary = "Health Check",
            description = "Verifica se a API está funcionando corretamente",
            tags = {"Health Check"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "API está funcionando",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            value = "{\n" +
                                    "  \"status\": \"UP\",\n" +
                                    "  \"timestamp\": \"2024-06-16T23:11:26Z\",\n" +
                                    "  \"service\": \"Loja - Mercado Pago Integration\"\n" +
                                    "}"
                    )
            )
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> healthResponse = new HashMap<>();
        healthResponse.put("status", "UP");
        healthResponse.put("timestamp", Instant.now().toString());
        healthResponse.put("service", "Loja - Mercado Pago Integration");

        return ResponseEntity.ok(healthResponse);
    }
}

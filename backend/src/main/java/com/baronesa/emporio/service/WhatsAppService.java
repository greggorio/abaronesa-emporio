package com.baronesa.emporio.service;

import com.baronesa.emporio.util.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class WhatsAppService {

    private final ConfigManager configManager;
    private final String runtimeServiceUrl;
    private final HttpClient httpClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Autowired
    public WhatsAppService(ConfigManager configManager,
                           @Value("${app.whatsapp.service-url:http://localhost:3001}") String runtimeServiceUrl) {
        this(configManager, runtimeServiceUrl, HttpClient.newHttpClient());
    }

    WhatsAppService(ConfigManager configManager, String runtimeServiceUrl, HttpClient httpClient) {
        this.configManager = configManager;
        this.runtimeServiceUrl = runtimeServiceUrl;
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return configManager.getBooleanConfig("whatsapp_enabled", false);
    }

    URI serviceUri(String endpoint) {
        String persisted = configManager.getConfig("whatsapp_service_url", "");
        String candidate = persisted != null && !persisted.isBlank() ? persisted : runtimeServiceUrl;
        try {
            URI base = URI.create(candidate == null ? "" : candidate.trim());
            String scheme = base.getScheme();
            String authority = base.getRawAuthority();
            boolean safeAuthority = authority != null && authority.matches("[A-Za-z0-9._-]+(?::[0-9]{1,5})?");
            if (!base.isAbsolute() || !safeAuthority
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("invalid");
            }
            String normalized = base.toString().replaceAll("/+$", "");
            return URI.create(normalized + endpoint);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Configuracao interna do WhatsApp invalida");
        }
    }

    private int maxAttachmentMb() {
        return configManager.getIntConfig("whatsapp_max_attachment_mb", 10);
    }

    private int sendTimeoutSeconds() {
        return configManager.getIntConfig("whatsapp_send_timeout_seconds", 15);
    }

    public Map<String, Object> status() {
        if (!isEnabled()) {
            return Map.of("conectado", false, "hasQr", false, "status", "DESABILITADO");
        }
        boolean conn = false;
        boolean hasQr = false;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/status"))
                    .GET()
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                conn = body.contains("\"connected\":true");
                hasQr = body.contains("\"hasQr\":true");
                connected.set(conn);
            }
        } catch (Exception e) {
            log.warn("Consulta de status do WhatsApp falhou");
        }
        String state = !isEnabled() ? "DESABILITADO" : (conn ? "CONECTADO" : "DESCONECTADO");
        return Map.of("conectado", conn, "hasQr", hasQr, "status", state);
    }

    public String fetchQrPng() {
        if (!isEnabled()) return null;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/qr"))
                    .GET()
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body().contains("png")) {
                int i = resp.body().indexOf("data:image");
                int j = resp.body().lastIndexOf('"');
                if (i >= 0 && j > i) {
                    return resp.body().substring(i, j);
                }
            }
        } catch (Exception e) {
            log.warn("Consulta de QR do WhatsApp falhou");
        }
        return null;
    }

    public boolean startIfNeeded() {
        if (!isEnabled()) return false;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/start"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log.warn("Inicializacao de sessao do WhatsApp falhou");
            return false;
        }
    }

    public boolean disconnect() {
        if (!isEnabled()) return false;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/disconnect"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            connected.set(false);
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log.warn("Desconexao do WhatsApp falhou");
            return false;
        }
    }

    public Map<String, Object> me() {
        if (!isEnabled()) return Map.of("success", false, "message", "disabled");
        try {
            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/me"))
                    .GET()
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                String wid = extractJsonValue(body, "wid");
                String push = extractJsonValue(body, "pushname");
                return Map.of("success", true, "wid", wid, "pushname", push);
            }
        } catch (Exception e) {
            log.warn("Consulta de identidade do WhatsApp falhou");
        }
        return Map.of("success", false);
    }

    public CompletableFuture<Void> enviarPdf(String telefone, byte[] pdfBytes, String nomeArquivo, String legenda) {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WhatsApp desabilitado"));
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("PDF vazio"));
        }
        long maxBytes = (long) maxAttachmentMb() * 1024L * 1024L;
        if (pdfBytes.length > maxBytes) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Arquivo excede o limite de " + maxAttachmentMb() + "MB"));
        }

        try {
            String phone = telefone != null ? telefone.replaceAll("\\D", "") : null;
            if (phone == null || phone.length() < 10) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Telefone inválido"));
            }
            String json = "{" +
                    "\"phone\":\"" + phone + "\"," +
                    "\"filename\":\"" + (nomeArquivo != null ? nomeArquivo : "documento.pdf") + "\"," +
                    "\"caption\":\"" + (legenda != null ? legenda.replace("\"", "'") : "Documento") + "\"," +
                    "\"pdfBase64\":\"" + Base64.getEncoder().encodeToString(pdfBytes) + "\"}";

            var req = HttpRequest.newBuilder()
                    .uri(serviceUri("/send-pdf"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(sendTimeoutSeconds(), TimeUnit.SECONDS)
                    .thenCompose(resp -> {
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body().contains("\"success\":true")) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.failedFuture(new RuntimeException("Falha ao enviar documento pelo WhatsApp"));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            int i = json.indexOf('"' + key + '"');
            if (i < 0) return null;
            int c = json.indexOf(':', i);
            int q1 = json.indexOf('"', c + 1);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 >= 0 && q2 > q1) return json.substring(q1 + 1, q2);
        } catch (Exception ignore) {
            // no-op
        }
        return null;
    }
}

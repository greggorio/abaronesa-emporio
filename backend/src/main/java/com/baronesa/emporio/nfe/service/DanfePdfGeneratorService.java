package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.nfe.dto.DanfeModel;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Gera PDF do DANFE (modelo 55).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DanfePdfGeneratorService {

    private final SpringTemplateEngine templateEngine;
    private final ConfigManager configManager;

    @Value("${nfe_logo_path:#{null}}")
    private String logoPath;

    public byte[] generateDanfePdf(DanfeModel danfe) throws Exception {
        Context context = new Context();
        ensureQrBase64(danfe);
        context.setVariable("danfe", danfe);
        context.setVariable("logoBase64", getLogoAsBase64());

        String htmlContent = templateEngine.process("danfe", context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String getLogoAsBase64() {
        String logoBase64Config = normalizeLogoConfig(configManager.getConfig("nfe_logo_base64", ""));
        if (!logoBase64Config.isBlank()) {
            return logoBase64Config;
        }

        // Caminho configurado
        String configPath = configManager.getConfig("nfe_logo_path", "");
        String pathToUse = !configPath.isBlank() ? configPath : logoPath;
        if (pathToUse != null && !pathToUse.isBlank()) {
            try {
                Path path = Paths.get(pathToUse);
                if (Files.exists(path)) {
                    byte[] logoBytes = Files.readAllBytes(path);
                    String mimeType = resolveMimeType(pathToUse);
                    return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(logoBytes);
                }
            } catch (Exception e) {
                log.warn("Falha ao carregar logo de {}: {}", pathToUse, e.getMessage());
            }
        }

        // Classpath fallback
        try {
            ClassPathResource resource = new ClassPathResource("static/images/logo.png");
            if (resource.exists()) {
                byte[] logoBytes = resource.getInputStream().readAllBytes();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(logoBytes);
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String normalizeLogoConfig(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("data:")) return trimmed;
        String sanitized = trimmed.replaceAll("\\s+", "");
        return "data:image/png;base64," + sanitized;
    }

    private String resolveMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/png";
    }

    private void ensureQrBase64(DanfeModel danfe) {
        if (danfe == null) return;
        if (danfe.getQrBase64() != null && !danfe.getQrBase64().isBlank()) return;
        String chave = danfe.getChaveAcesso();
        if (chave == null || chave.isBlank()) return;
        try {
            danfe.setQrBase64(gerarQrBase64(chave));
        } catch (Exception e) {
            log.warn("Falha ao gerar QR Code DANFE: {}", e.getMessage());
        }
    }

    private String gerarQrBase64(String conteudo) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(conteudo, BarcodeFormat.QR_CODE, 110, 110, hints);

        BufferedImage qrImage = new BufferedImage(110, 110, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = qrImage.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 110, 110);
        graphics.setColor(Color.BLACK);

        for (int x = 0; x < 110; x++) {
            for (int y = 0; y < 110; y++) {
                if (bitMatrix.get(x, y)) {
                    graphics.fillRect(x, y, 1, 1);
                }
            }
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(qrImage, "PNG", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            throw new WriterException(e);
        }
    }
}

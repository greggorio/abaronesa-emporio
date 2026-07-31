package com.baronesa.emporio.nfe.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.baronesa.emporio.nfe.dto.DanfceModel;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Serviço especializado para geração de PDF do DANFCE (Documento Auxiliar da NFC-e).
 *
 * Responsabilidades:
 * - Gerar PDF otimizado para impressão térmica 80mm
 * - Criar QR Code dinâmico para consulta da NFC-e
 * - Processar template Thymeleaf específico para DANFCE
 * - Suportar múltiplos formatos de impressão (A4, Térmica)
 *
 * @author Sistema Loja (Ported to Bares)
 * @since 2025-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DanfcePdfGeneratorService {

    private final SpringTemplateEngine templateEngine;
    private final ConfigManager configManager;

    /**
     * Caminho para o logo da empresa (opcional)
     */
    @Value("${nfe_logo_path:#{null}}")
    private String logoPath;

    /**
     * Enum para definir modos de impressão do DANFCE
     */
    public enum ModoImpressao {
        TERMICA_80MM("80mm", "termica"),
        A4("A4", "a4"),
        TERMICA_58MM("58mm", "termica58");

        private final String largura;
        private final String template;

        ModoImpressao(String largura, String template) {
            this.largura = largura;
            this.template = template;
        }

        public String getLargura() { return largura; }
        public String getTemplate() { return template; }
    }

    /**
     * Gera PDF do DANFCE com configurações otimizadas
     */
    public byte[] generateDanfcePdf(DanfceModel danfce) throws Exception {
        return generateDanfcePdf(danfce, ModoImpressao.TERMICA_80MM);
    }

    /**
     * Gera PDF do DANFCE com modo de impressão específico
     */
    public byte[] generateDanfcePdf(DanfceModel danfce, ModoImpressao modo) throws Exception {
        log.info("Gerando DANFCE PDF - Chave: {} - Modo: {}",
                danfce.getChaveAcesso(), modo.name());

        try {
            // Preparar contexto Thymeleaf
            Context context = prepararContexto(danfce);

            // Gerar QR Code se necessário
            adicionarQRCode(context, danfce);

            // Adicionar logo se configurado
            adicionarLogo(context);

            // Processar template específico para o modo
            String templateName = obterNomeTemplate(modo);
            String htmlContent = templateEngine.process(templateName, context);

            log.debug("Template processado com sucesso: {}", templateName);

            // Converter HTML para PDF
            byte[] pdfBytes = converterHtmlParaPdf(htmlContent, modo);

            log.info("DANFCE PDF gerado com sucesso - Tamanho: {} bytes", pdfBytes.length);

            return pdfBytes;

        } catch (Exception e) {
            log.error("Erro ao gerar DANFCE PDF", e);
            throw new Exception("Falha na geração do DANFCE: " + e.getMessage(), e);
        }
    }

    /**
     * Prepara o contexto Thymeleaf com dados do DANFCE
     */
    private Context prepararContexto(DanfceModel danfce) {
        Context context = new Context();

        // Dados principais
        context.setVariable("danfce", danfce);

        // Configurações do sistema
        context.setVariable("sistemaVersao",
                configManager.getConfig("nfe_versao_aplicativo", "Loja SmartData v2.0"));

        // Data/hora atual para rodapé
        context.setVariable("dataAtual",
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        // URLs e configurações de consulta
        context.setVariable("urlConsultaNFCe", danfce.getUrlConsulta());
        
        // Cálculo da altura dinâmica da página (em mm)
        // Base aumentada para incluir QR Code (60mm) + Cabeçalhos + Rodapés + Margem de segurança
        int baseHeight = 140; 
        int itemHeight = 15; // 3 linhas de texto + separador
        int pagamentoHeight = 5;
        
        int qtdItens = danfce.getProdutos() != null ? danfce.getProdutos().size() : 0;
        int qtdPagamentos = 0;
        if (danfce.getPagamentos() != null && !danfce.getPagamentos().isEmpty()) {
            qtdPagamentos = danfce.getPagamentos().size();
        } else if (danfce.getFormaPagamento() != null) {
            qtdPagamentos = 1;
        }

        int totalHeight = baseHeight +
                         (qtdItens * itemHeight) +
                         (qtdPagamentos * pagamentoHeight);
        if (danfce.getValorAcrescimo() != null && !"R$ 0,00".equals(danfce.getValorAcrescimo())) {
            totalHeight += 5;
        }
        
        context.setVariable("pageHeight", totalHeight);

        log.debug("Contexto preparado com {} produtos. Altura calc: {}mm",
                qtdItens, totalHeight);

        return context;
    }

    /**
     * Gera e adiciona QR Code ao contexto
     */
    private void adicionarQRCode(Context context, DanfceModel danfce) {
        if (danfce.getQrCode() == null || danfce.getQrCode().trim().isEmpty()) {
            log.warn("QR Code não encontrado para DANFCE: {}", danfce.getChaveAcesso());
            return;
        }

        try {
            String qrCodeBase64 = gerarQRCodeBase64(danfce.getQrCode());
            context.setVariable("qrCodeBase64", qrCodeBase64);

            log.debug("QR Code gerado com sucesso - Tamanho: {} chars", qrCodeBase64.length());

        } catch (Exception e) {
            log.error("Erro ao gerar QR Code para DANFCE", e);
            // Continua sem QR Code - não é crítico
        }
    }

    /**
     * Gera QR Code em formato Base64
     */
    private String gerarQRCodeBase64(String conteudo) throws WriterException, IOException {
        // Configurações do QR Code otimizadas para impressão térmica
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        // Gerar matriz do QR Code
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(conteudo, BarcodeFormat.QR_CODE, 200, 200, hints);

        // Converter para imagem
        BufferedImage qrImage = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        qrImage.createGraphics();

        Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 200, 200);
        graphics.setColor(Color.BLACK);

        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                if (bitMatrix.get(x, y)) {
                    graphics.fillRect(x, y, 1, 1);
                }
            }
        }

        // Converter para Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Adiciona logo da empresa ao contexto
     */
    private void adicionarLogo(Context context) {
        try {
            String logoBase64 = obterLogoBase64();
            if (logoBase64 != null && !logoBase64.isEmpty()) {
                context.setVariable("logoBase64", logoBase64);
                log.debug("Logo adicionado ao contexto");
            }
        } catch (Exception e) {
            log.warn("Não foi possível carregar o logo: {}", e.getMessage());
        }
    }

    /**
     * Obtém logo como Data URI Base64
     */
    private String obterLogoBase64() {
        String logoBase64Config = normalizeLogoConfig(configManager.getConfig("nfe_logo_base64", ""));
        if (!logoBase64Config.isBlank()) {
            return logoBase64Config;
        }

        // 1. Tentar caminho configurado
        String configPath = configManager.getConfig("nfe_logo_path", "");
        String pathToUse = !configPath.isBlank() ? configPath : logoPath;
        if (pathToUse != null && !pathToUse.trim().isEmpty()) {
            try {
                Path path = Paths.get(pathToUse);
                if (Files.exists(path)) {
                    byte[] logoBytes = Files.readAllBytes(path);
                    String mimeType = obterMimeType(pathToUse);
                    String base64 = Base64.getEncoder().encodeToString(logoBytes);
                    return "data:" + mimeType + ";base64," + base64;
                }
            } catch (Exception e) {
                log.debug("Erro ao carregar logo do caminho configurado: {}", e.getMessage());
            }
        }

        // 2. Tentar recursos do classpath
        String[] recursosLogo = {"static/images/logo.png", "static/images/logo.jpg", "static/images/danfe.jpg"};

        for (String recurso : recursosLogo) {
            try {
                ClassPathResource resource = new ClassPathResource(recurso);
                if (resource.exists()) {
                    byte[] logoBytes = resource.getInputStream().readAllBytes();
                    String mimeType = recurso.endsWith(".png") ? "image/png" : "image/jpeg";
                    String base64 = Base64.getEncoder().encodeToString(logoBytes);
                    return "data:" + mimeType + ";base64," + base64;
                }
            } catch (Exception e) {
                log.debug("Erro ao carregar logo do classpath {}: {}", recurso, e.getMessage());
            }
        }

        // 3. Gerar SVG simples como fallback
        return gerarLogoSvgFallback();
    }

    private String normalizeLogoConfig(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("data:")) return trimmed;
        String sanitized = trimmed.replaceAll("\\s+", "");
        return "data:image/png;base64," + sanitized;
    }

    /**
     * Determina MIME type por extensão
     */
    private String obterMimeType(String caminho) {
        String lower = caminho.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/png"; // padrão
    }

    /**
     * Gera logo SVG simples como fallback
     */
    private String gerarLogoSvgFallback() {
        String svg = "<svg width='80' height='40' xmlns='http://www.w3.org/2000/svg'>" +
                "<rect width='80' height='40' fill='#f0f0f0' stroke='#ccc'/>" +
                "<text x='40' y='25' text-anchor='middle' font-family='Arial' font-size='12'>LOGO</text>" +
                "</svg>";

        String base64 = Base64.getEncoder().encodeToString(svg.getBytes());
        return "data:image/svg+xml;base64," + base64;
    }

    /**
     * Determina nome do template baseado no modo de impressão
     */
    private String obterNomeTemplate(ModoImpressao modo) {
        // Por enquanto, usar o mesmo template para todos os modos
        // Futuramente pode haver templates específicos para cada formato
        switch (modo) {
            case TERMICA_58MM:
                return "danfce-58mm"; // Template específico para 58mm (se existir)
            case A4:
                return "danfce-a4";   // Template específico para A4 (se existir)
            case TERMICA_80MM:
            default:
                return "danfce_80mm";      // Template padrão 80mm
        }
    }

    /**
     * Converte HTML para PDF com configurações específicas do modo
     */
    private byte[] converterHtmlParaPdf(String htmlContent, ModoImpressao modo) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            ITextRenderer renderer = new ITextRenderer();

            // Configurações específicas por modo de impressão
            configurarRenderer(renderer, modo);

            // Processar HTML
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);

            return outputStream.toByteArray();

        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                log.warn("Erro ao fechar stream: {}", e.getMessage());
            }
        }
    }

    /**
     * Configura renderer com parâmetros específicos para cada modo
     */
    private void configurarRenderer(ITextRenderer renderer, ModoImpressao modo) {
        // Configurações específicas podem ser adicionadas aqui
        // Por exemplo, diferentes fontes, margens, etc.

        switch (modo) {
            case TERMICA_58MM:
                // Configurações específicas para 58mm
                break;
            case A4:
                // Configurações específicas para A4
                break;
            case TERMICA_80MM:
            default:
                // Configurações padrão para 80mm
                break;
        }
    }

    /**
     * Gera DANFCE em formato HTML para prévia
     */
    public String generateDanfceHtml(DanfceModel danfce) throws Exception {
        log.info("Gerando DANFCE HTML para prévia - Chave: {}", danfce.getChaveAcesso());

        try {
            Context context = prepararContexto(danfce);
            adicionarQRCode(context, danfce);
            adicionarLogo(context);

            String htmlContent = templateEngine.process("danfce_80mm", context);

            log.info("DANFCE HTML gerado com sucesso");
            return htmlContent;

        } catch (Exception e) {
            log.error("Erro ao gerar DANFCE HTML", e);
            throw new Exception("Falha na geração do HTML DANFCE: " + e.getMessage(), e);
        }
    }

    /**
     * Determina modo de impressão baseado na configuração
     */
    public ModoImpressao obterModoImpressaoConfigurado() {
        String modo = configManager.getConfig("nfce_modo_impressao", "TERMICA");

        try {
            switch (modo.toUpperCase()) {
                case "TERMICA":
                case "TERMICA_80MM":
                case "80MM":
                    return ModoImpressao.TERMICA_80MM;
                case "TERMICA_58MM":
                case "58MM":
                    return ModoImpressao.TERMICA_58MM;
                case "A4":
                    return ModoImpressao.A4;
                default:
                    log.warn("Modo de impressão desconhecido: {}. Usando padrão TERMICA_80MM", modo);
                    return ModoImpressao.TERMICA_80MM;
            }
        } catch (Exception e) {
            log.warn("Erro ao determinar modo de impressão: {}. Usando padrão", e.getMessage());
            return ModoImpressao.TERMICA_80MM;
        }
    }

    /**
     * Valida se o sistema está configurado para gerar DANFCE
     */
    public boolean isConfiguradoParaDanfce() {
        try {
            // Verificar se as configurações mínimas estão presentes
            String tokenCSC = configManager.getConfig("nfe_token_csc", "");
            String idCSC = configManager.getConfig("nfe_id_csc", "");

            boolean configurado = !tokenCSC.isEmpty() && !idCSC.isEmpty();

            if (!configurado) {
                log.warn("DANFCE não configurado - CSC ausente");
            }

            return configurado;

        } catch (Exception e) {
            log.error("Erro ao verificar configuração DANFCE", e);
            return false;
        }
    }

    /**
     * Obtém informações sobre capacidades de impressão
     */
    public Map<String, Object> obterInformacoesDanfce() {
        Map<String, Object> info = new java.util.HashMap<>();

        info.put("configurado", isConfiguradoParaDanfce());
        info.put("modoImpressao", obterModoImpressaoConfigurado().name());
        info.put("templateDisponivel", templateEngine != null);
        info.put("logoConfigurado", logoPath != null && !logoPath.trim().isEmpty());

        return info;
    }
}

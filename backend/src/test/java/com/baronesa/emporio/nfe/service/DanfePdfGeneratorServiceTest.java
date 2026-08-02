package com.baronesa.emporio.nfe.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.baronesa.emporio.nfe.dto.DanfeModel;
import com.baronesa.emporio.util.ConfigManager;

/**
 * Prova funcional do renderer DANFE efetivamente usado pela aplicação:
 * Thymeleaf + Flying Saucer/OpenPDF. Sem Spring Boot, banco, rede ou fixture
 * binária.
 */
class DanfePdfGeneratorServiceTest {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Test
    void generatesRealPdfThroughTheActiveRenderer() throws Exception {
        DanfePdfGeneratorService service =
                new DanfePdfGeneratorService(templateEngine(), configManagerReturningDefaults());

        byte[] pdf = service.generateDanfePdf(minimalDanfe());

        assertNotNull(pdf);
        assertTrue(pdf.length > PDF_MAGIC.length, "PDF gerado não pode ser vazio");
        assertTrue(startsWithPdfMagic(pdf), "Saída deve começar por %PDF-");
    }

    private static boolean startsWithPdfMagic(byte[] pdf) {
        if (pdf.length < PDF_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (pdf[index] != PDF_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static ConfigManager configManagerReturningDefaults() {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return configManager;
    }

    private static DanfeModel minimalDanfe() {
        DanfeModel danfe = new DanfeModel();
        danfe.setProdutos(List.of());
        danfe.setDuplicatas(List.of());
        return danfe;
    }
}

package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ConsumoRequestDTO;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsumoReceiptService {

    private final TemplateEngine templateEngine;
    private final ConfigManager configManager;
    private final PdfGeneratorService pdfGeneratorService;

    public byte[] generateConsumoReceiptPdf(ConsumoRequestDTO consumoRequest) throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        
        // Add consumption data
        context.put("valorTotal", consumoRequest.getValorTotal());
        context.put("nomeCliente", consumoRequest.getNomeCliente());
        
        // Add current date and time
        LocalDateTime now = LocalDateTime.now();
        context.put("data", now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.put("hora", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        // Recuperar dados da empresa das configurações
        String razaoSocial = configManager.getConfig("nfe_razao_social", "EMPRESA NÃO CONFIGURADA");
        String cnpj = configManager.getConfig("nfe_cnpj", "00.000.000/0000-00");

        String endereco = String.format("%s, %s - %s, %s/%s",
            configManager.getConfig("nfe_logradouro", ""),
            configManager.getConfig("nfe_numero", ""),
            configManager.getConfig("nfe_bairro", ""),
            configManager.getConfig("nfe_municipio", ""),
            configManager.getConfig("nfe_uf", "")
        );

        if (endereco.equals(",  - , /")) {
             endereco = "Endereço não configurado";
        }

        context.put("empresaRazaoSocial", razaoSocial);
        context.put("empresaCnpj", cnpj);
        context.put("empresaEndereco", endereco);

        // Define page height (simpler calculation for consumption receipt)
        int pageHeight = 80; // Fixed height for simpler layout
        context.put("pageHeight", pageHeight);

        return pdfGeneratorService.generatePdfFromTemplate("comprovante-consumo", context);
    }
}
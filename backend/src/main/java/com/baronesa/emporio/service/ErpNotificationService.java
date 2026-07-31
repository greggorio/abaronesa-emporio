package com.baronesa.emporio.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;

@Service
public class ErpNotificationService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final String PRIORITY_URGENTE = "URGENTE";
    private static final String PRIORITY_BAIXA = "BAIXA";
    private static final String PRIORITY_MEDIA = "MEDIA";
    private static final String PRIORITY_ALTA = "ALTA";

    // Formatter para data/hora no formato brasileiro
    private static final DateTimeFormatter BRAZILIAN_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm'hrs'");

    // ========== MÉTODOS PARA SITUAÇÕES ESPECÍFICAS DO ERP ==========

    /**
     * 1. Notifica sobre ajuste de estoque realizado
     * @param skuCodigo Código do SKU ajustado
     * @param skuDescricao Descrição do produto/SKU
     * @param quantidadeAjustada Quantidade ajustada (positiva ou negativa)
     * @param estoqueAnterior Estoque antes do ajuste
     * @param estoqueAtual Estoque após o ajuste
     * @param motivo Motivo do ajuste
     * @param usuarioResponsavelId ID do usuário que realizou o ajuste
     */
    @Transactional
    public void notifyStockAdjustment(String skuCodigo, String skuDescricao,
                                      BigDecimal quantidadeAjustada, BigDecimal estoqueAnterior,
                                      BigDecimal estoqueAtual, String motivo, Long usuarioResponsavelId) {
        try {
            // 1. NOTIFICAÇÃO PARA ADMINS
            Map<String, Object> adminNotificationData = new HashMap<>();
            adminNotificationData.put("titulo", "📦 Ajuste de Estoque Realizado");
            adminNotificationData.put("mensagem", buildStockAdjustmentMessage(
                    skuCodigo, skuDescricao, quantidadeAjustada, estoqueAnterior,
                    estoqueAtual, motivo, usuarioResponsavelId
            ));
            adminNotificationData.put("tipo", "ROLE");
            adminNotificationData.put("role", "ADMIN");
            adminNotificationData.put("importancia", PRIORITY_ALTA);
            adminNotificationData.put("createdById", getSystemUserId());

            notificationService.create(adminNotificationData);

            // 2. NOTIFICAÇÃO PARA GERENTES DE ESTOQUE
            Map<String, Object> stockManagerNotificationData = new HashMap<>();
            stockManagerNotificationData.put("titulo", "📦 Ajuste de Estoque");
            stockManagerNotificationData.put("mensagem", buildStockAdjustmentMessage(
                    skuCodigo, skuDescricao, quantidadeAjustada, estoqueAnterior,
                    estoqueAtual, motivo, usuarioResponsavelId
            ));
            stockManagerNotificationData.put("tipo", "ROLE");
            stockManagerNotificationData.put("role", "FUNCIONARIO");
            stockManagerNotificationData.put("importancia", PRIORITY_MEDIA);
            stockManagerNotificationData.put("createdById", getSystemUserId());

            notificationService.create(stockManagerNotificationData);

        } catch (Exception e) {
            System.err.println("Erro ao notificar ajuste de estoque: " + e.getMessage());
        }
    }

    /**
     * 2. Notifica sobre nova NFe detectada para o CNPJ da empresa
     * @param numeroNFe Número da nota fiscal
     * @param chaveNFe Chave de acesso da NFe
     * @param fornecedorNome Nome do fornecedor/emitente
     * @param fornecedorCNPJ CNPJ do fornecedor
     * @param valorTotal Valor total da NFe
     * @param dataEmissao Data de emissão da NFe
     * @param numeroItens Quantidade de itens na NFe
     */
    @Transactional
    public void notifyNewNFeDetected(String numeroNFe, String chaveNFe, String fornecedorNome,
                                     String fornecedorCNPJ, BigDecimal valorTotal,
                                     String dataEmissao, Integer numeroItens) {
        try {
            // 1. NOTIFICAÇÃO URGENTE PARA ADMINS
            Map<String, Object> adminNotificationData = new HashMap<>();
            adminNotificationData.put("titulo", "🔔 Nova NFe Detectada!");
            adminNotificationData.put("mensagem", buildNewNFeMessage(
                    numeroNFe, chaveNFe, fornecedorNome, fornecedorCNPJ,
                    valorTotal, dataEmissao, numeroItens
            ));
            adminNotificationData.put("tipo", "ROLE");
            adminNotificationData.put("role", "ADMIN");
            adminNotificationData.put("importancia", PRIORITY_URGENTE);
            adminNotificationData.put("createdById", getSystemUserId());

            notificationService.create(adminNotificationData);

            // 2. NOTIFICAÇÃO PARA FUNCIONÁRIOS DO FISCAL/COMPRAS
            Map<String, Object> fiscalNotificationData = new HashMap<>();
            fiscalNotificationData.put("titulo", "📄 Nova NFe para Processamento");
            fiscalNotificationData.put("mensagem", buildNewNFeMessage(
                    numeroNFe, chaveNFe, fornecedorNome, fornecedorCNPJ,
                    valorTotal, dataEmissao, numeroItens
            ));
            fiscalNotificationData.put("tipo", "ROLE");
            fiscalNotificationData.put("role", "FUNCIONARIO");
            fiscalNotificationData.put("importancia", PRIORITY_ALTA);
            fiscalNotificationData.put("createdById", getSystemUserId());

            notificationService.create(fiscalNotificationData);

        } catch (Exception e) {
            System.err.println("Erro ao notificar nova NFe: " + e.getMessage());
        }
    }

    /**
     * 3. Notifica sobre nova versão Docker disponível
     * @param clientName Nome do cliente (ex: 'erp', 'smartdata')
     * @param componentType Tipo do componente ('backend' ou 'frontend')
     * @param currentVersion Versão atual instalada
     * @param availableVersion Nova versão disponível
     * @param repositoryName Nome do repositório Docker
     */
    @Transactional
    public void notifyDockerVersionUpdate(String clientName, String componentType,
                                        String currentVersion, String availableVersion,
                                        String repositoryName) {
        try {
            // Verificar se já existe notificação similar não lida para evitar duplicatas
            String searchTerm = String.format("%s:%s", clientName, componentType);
            if (hasUnreadDockerNotification(searchTerm, availableVersion)) {
                System.out.println("Notificação Docker já existe para " + searchTerm + " - pulando duplicata");
                return;
            }

            // 1. NOTIFICAÇÃO PARA ADMINS
            Map<String, Object> adminNotificationData = new HashMap<>();
            adminNotificationData.put("titulo", "🐳 Nova versão Docker disponível");
            adminNotificationData.put("mensagem", buildDockerVersionMessage(
                    clientName, componentType, currentVersion, availableVersion, repositoryName
            ));
            adminNotificationData.put("tipo", "ROLE");
            adminNotificationData.put("role", "ADMIN");
            adminNotificationData.put("importancia", PRIORITY_ALTA);
            adminNotificationData.put("createdById", getSystemUserId());

            notificationService.create(adminNotificationData);

            // 2. NOTIFICAÇÃO PARA FUNCIONÁRIOS
            Map<String, Object> funcionarioNotificationData = new HashMap<>();
            funcionarioNotificationData.put("titulo", "📦 Atualização de sistema disponível");
            funcionarioNotificationData.put("mensagem", buildDockerVersionMessage(
                    clientName, componentType, currentVersion, availableVersion, repositoryName
            ));
            funcionarioNotificationData.put("tipo", "ROLE");
            funcionarioNotificationData.put("role", "FUNCIONARIO");
            funcionarioNotificationData.put("importancia", PRIORITY_MEDIA);
            funcionarioNotificationData.put("createdById", getSystemUserId());

            notificationService.create(funcionarioNotificationData);

        } catch (Exception e) {
            System.err.println("Erro ao notificar nova versão Docker: " + e.getMessage());
        }
    }

    // ========== MÉTODOS PRIVADOS PARA CONSTRUÇÃO DAS MENSAGENS ==========

    /**
     * Constrói mensagem para ajuste de estoque
     */
    private String buildStockAdjustmentMessage(String skuCodigo, String skuDescricao,
                                               BigDecimal quantidadeAjustada, BigDecimal estoqueAnterior,
                                               BigDecimal estoqueAtual, String motivo, Long usuarioResponsavelId) {
        StringBuilder message = new StringBuilder();
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00", symbols);

        message.append("Um ajuste de estoque foi realizado.\n\n");

        if (skuCodigo != null) {
            message.append("📦 SKU: ").append(skuCodigo).append("\n");
        }

        if (skuDescricao != null) {
            message.append("📋 Produto: ").append(skuDescricao).append("\n");
        }

        message.append("\n");

        // Indicador visual do tipo de ajuste
        if (quantidadeAjustada.compareTo(BigDecimal.ZERO) > 0) {
            message.append("✅ Tipo: ENTRADA (Aumento de estoque)\n");
            message.append("➕ Quantidade ajustada: +").append(decimalFormat.format(quantidadeAjustada)).append("\n");
        } else {
            message.append("❌ Tipo: SAÍDA (Redução de estoque)\n");
            message.append("➖ Quantidade ajustada: ").append(decimalFormat.format(quantidadeAjustada)).append("\n");
        }

        message.append("\n");
        message.append("📊 Estoque anterior: ").append(decimalFormat.format(estoqueAnterior)).append("\n");
        message.append("📊 Estoque atual: ").append(decimalFormat.format(estoqueAtual)).append("\n");

        if (motivo != null && !motivo.trim().isEmpty()) {
            message.append("\n💬 Motivo: ").append(motivo).append("\n");
        }

        // Buscar nome do usuário responsável
        if (usuarioResponsavelId != null) {
            String nomeUsuario = getUsuarioNome(usuarioResponsavelId);
            if (nomeUsuario != null) {
                message.append("\n👤 Responsável: ").append(nomeUsuario).append("\n");
            }
        }

        message.append("\n⏰ Realizado em: ").append(getCurrentBrazilianDateTime());

        return message.toString();
    }

    /**
     * Constrói mensagem para nova NFe detectada
     */
    private String buildNewNFeMessage(String numeroNFe, String chaveNFe, String fornecedorNome,
                                      String fornecedorCNPJ, BigDecimal valorTotal,
                                      String dataEmissao, Integer numeroItens) {
        StringBuilder message = new StringBuilder();
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
        DecimalFormat currencyFormat = new DecimalFormat("R$ #,##0.00", symbols);

        message.append("Uma nova Nota Fiscal Eletrônica foi detectada para o CNPJ da empresa.\n");
        message.append("Esta NFe precisa ser processada e importada no sistema.\n\n");

        message.append("📄 DADOS DA NOTA FISCAL:\n");
        message.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        if (numeroNFe != null) {
            message.append("📋 Número: ").append(numeroNFe).append("\n");
        }

        if (dataEmissao != null) {
            message.append("📅 Data de Emissão: ").append(dataEmissao).append("\n");
        }

        if (fornecedorNome != null) {
            message.append("🏢 Fornecedor: ").append(fornecedorNome).append("\n");
        }

        if (fornecedorCNPJ != null) {
            message.append("📝 CNPJ: ").append(formatarCNPJ(fornecedorCNPJ)).append("\n");
        }

        if (valorTotal != null) {
            message.append("💰 Valor Total: ").append(currencyFormat.format(valorTotal)).append("\n");
        }

        if (numeroItens != null) {
            message.append("📦 Quantidade de Itens: ").append(numeroItens).append("\n");
        }

        if (chaveNFe != null) {
            message.append("\n🔑 Chave de Acesso:\n").append(formatarChaveNFe(chaveNFe)).append("\n");
        }

        message.append("\n⚠️ AÇÃO NECESSÁRIA:\n");
        message.append("Esta NFe deve ser importada no sistema para:\n");
        message.append("• Atualização do estoque\n");
        message.append("• Registro fiscal\n");
        message.append("• Controle de contas a pagar\n");

        message.append("\n⏰ Detectada em: ").append(getCurrentBrazilianDateTime());

        return message.toString();
    }

    /**
     * Constrói mensagem para nova versão Docker disponível
     */
    private String buildDockerVersionMessage(String clientName, String componentType,
                                           String currentVersion, String availableVersion,
                                           String repositoryName) {
        StringBuilder message = new StringBuilder();

        message.append("Uma nova versão está disponível no Docker Hub.\n\n");

        message.append("🏢 Cliente: ").append(clientName).append("\n");
        message.append("📦 Componente: ").append(componentType.toUpperCase()).append("\n");
        message.append("🏷️ Repositório: ").append(repositoryName != null ? repositoryName : "N/A").append("\n\n");

        message.append("📊 VERSÕES:\n");
        message.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        message.append("🔹 Versão Atual: ").append(currentVersion).append("\n");
        message.append("🆕 Nova Versão: ").append(availableVersion).append("\n\n");

        message.append("⚠️ AÇÃO RECOMENDADA:\n");
        message.append("• Revisar changelog da nova versão\n");
        message.append("• Testar em ambiente de desenvolvimento\n");
        message.append("• Agendar atualização em horário apropriado\n");
        message.append("• Fazer backup antes da atualização\n\n");

        message.append("⏰ Detectado em: ").append(getCurrentBrazilianDateTime());

        return message.toString();
    }

    /**
     * Formata CNPJ para exibição
     */
    private String formatarCNPJ(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return String.format("%s.%s.%s/%s-%s",
                cnpj.substring(0, 2),
                cnpj.substring(2, 5),
                cnpj.substring(5, 8),
                cnpj.substring(8, 12),
                cnpj.substring(12, 14)
        );
    }

    /**
     * Formata chave NFe para exibição (em blocos de 4 dígitos)
     */
    private String formatarChaveNFe(String chave) {
        if (chave == null || chave.length() != 44) {
            return chave;
        }
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < chave.length(); i += 4) {
            if (i > 0) formatted.append(" ");
            formatted.append(chave.substring(i, Math.min(i + 4, chave.length())));
        }
        return formatted.toString();
    }

    /**
     * Busca o nome do usuário pelo ID
     */
    private String getUsuarioNome(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getNome)
                .orElse("Usuário ID: " + usuarioId);
    }

    /**
     * Retorna a data/hora atual no formato brasileiro
     * Formato: dd/MM/yyyy HH:mmhrs
     */
    private String getCurrentBrazilianDateTime() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        return now.format(BRAZILIAN_DATETIME_FORMATTER);
    }

    /**
     * Verifica se já existe notificação Docker não lida para evitar duplicatas
     */
    private boolean hasUnreadDockerNotification(String clientComponent, String availableVersion) {
        try {
            return notificationService.hasUnreadNotificationContaining(
                "🐳 Nova versão Docker disponível", clientComponent
            );
        } catch (Exception e) {
            // Em caso de erro, permitir criação da notificação
            System.err.println("Erro ao verificar notificações existentes: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca o ID do usuário SYSTEM para notificações automáticas
     * @return ID do usuário SYSTEM ou null se não encontrado
     */
    private Long getSystemUserId() {
        return usuarioRepository.findByRolesContaining(Usuario.Role.SYSTEM)
                .stream()
                .findFirst()
                .map(Usuario::getId)
                .orElse(null);
    }
}
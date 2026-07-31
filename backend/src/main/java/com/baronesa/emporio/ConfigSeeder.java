package com.baronesa.emporio;

import com.baronesa.emporio.entity.Configuracao;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.repository.ConfiguracaoRepository;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeders.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigSeeder implements CommandLineRunner {

    private final ConfiguracaoRepository configuracaoRepository;
    private final TipoReceitaRepository tipoReceitaRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${espresso.sync.base-url:http://localhost:8085}")
    private String espressoSyncBaseUrl;

    @Value("${website.sync.api-key:}")
    private String websiteSyncApiKey;

    @Value("${espresso-api.app.base-url:${ESPRESSO_API_BASE_URL:http://localhost:8085}}")
    private String espressoApiBaseUrl;

    @Value("${app.fiscal.nfe-schema-path}")
    private String nfeSchemaPath;

    @Value("${app.fiscal.nfe-xml-path}")
    private String nfeXmlPath;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=============================================");
        log.info("INICIANDO VERIFICAÇÃO DE SEED DE CONFIGURAÇÕES");
        log.info("=============================================");

        // Tipo de receita específico para excedente de voucher
        seedTipoReceita("Excedente de voucher");

        // Idioma do ERP
        seedConfig("erp_language", "pt_BR", "Idioma do ERP", "Idioma principal do sistema (ex.: pt_BR, en_US, es_ES)");

        // Identidade da Aplicação
        seedConfig("app_name", "", "Nome da aplicação", "Nome exibido na aplicação (ERP)");
        seedConfig("segmento", "", "Segmento do negócio", "Segmento atendido (ex.: bar, restaurante, cafeteria)");

        // Taxas e Serviços
        seedConfig("taxa_servico_percentual", "10", "Taxa de serviço (%)", "Percentual padrão da taxa de serviço/gorjeta sugerida");
        seedConfig("taxa_servico_ativa", "true", "Taxa de serviço ativa", "Flag que indica se a taxa de serviço está habilitada");
        seedConfig("couvert_artistico_ativo", "false", "Couvert artístico ativo", "Flag que indica se o couvert artístico está habilitado");

        // Cardápio Digital (Novas Configurações)
        seedConfig("site_cardapio_solicita_cpf", "false", "Solicitar CPF no Cardápio", "Define se o campo CPF deve ser solicitado ao cliente durante o pedido no cardápio digital.");
        seedConfig("site_cardapio_solicita_telefone", "false", "Solicitar Telefone no Cardápio", "Define se o campo Telefone deve ser solicitado ao cliente durante o pedido no cardápio digital.");
        seedConfig("site_service_mode", "waiter_delivery", "Modo de serviço (garçom ou retirada)", "Define se itens prontos são entregues pelo garçom (waiter_delivery) ou retirados no balcão (customer_pickup).");

        // Gamificação
        seedConfig("gamificacao_ativo", "true", "Gamificação ativa", "Habilita ou desabilita o cálculo de pontos automatizado.");
        seedConfig("gamificacao_valor_para_1_ponto", "5.00", "Valor para 1 ponto", "Valor em reais que corresponde a 1 ponto na gamificação.");
        seedConfig("gamificacao_arredondamento", "FLOOR", "Arredondamento de pontos", "Define como o valor resultante deve ser arredondado (FLOOR, CEIL ou ROUND).");
        seedConfig("gamificacao_expiracao_pontos_em_dias", "0", "Expiração de pontos (dias)", "Número de dias antes de expirar os pontos (0 = nunca expira).");

        // Configurações NF-e
        seedConfig("nfe_ambiente", "2", "Ambiente NFe", "Ambiente NFe (1-Produção, 2-Homologação)");
        seedConfig("nfe_serie", "1", "Série NFe", "Número de série da NFe");
        seedConfig("nfe_numero", "", "Número NFe", "Número sequencial da NFe");
        seedConfig("nfe_cfop_padrao", "5102", "CFOP Padrão", "Código CFOP padrão para vendas");
        seedConfig("nfe_consulta_automatica", "true", "Consulta Automática NFe", "Habilita/desabilita a consulta automática de NFe");
        seedConfig("nfe_consulta_intervalo_minutos", "60", "Intervalo de consulta NFe (min)", "Intervalo padrão em minutos para consultar autorizações.");
        seedConfig("nfe_modo", "normal", "Modo de emissão NFe", "Modo de emissão: normal ou contingência.");
        seedConfig("nfe_manifestar_automatico", "false", "Manifestação automática NFe", "Habilita manifestação automática de notas recebidas.");
        seedConfig("nfe_timeout_conexao", "30000", "Timeout de conexão NFe (ms)", "Timeout em milissegundos para abrir conexão com a SEFAZ.");
        seedConfig("nfe_timeout_leitura", "60000", "Timeout de leitura NFe (ms)", "Timeout em milissegundos para ler respostas da SEFAZ.");
        seedConfig("nfe_usar_virtual_threads", "true", "NFe usando virtual threads", "Processa operações de NFe com virtual threads.");
        seedConfig("nfe_versao_aplicativo", "Loja 1.0", "Versão do aplicativo NFe", "Identificação do aplicativo emissor.");
        seedConfig("nfe_url_distribuicao", "", "URL distribuição NFe (custom)", "Override opcional da URL de distribuição de DF-e.");
        seedConfig("nfe_url_manifestacao", "", "URL manifestação NFe (custom)", "Override opcional da URL de manifestação do destinatário.");

        // Certificado e schemas da NFe
        seedConfig("nfe_certificado_path", "", "Caminho certificado NFe", "Path absoluto do certificado .pfx para assinatura.");
        seedConfig("nfe_certificado_senha", "", "Senha do certificado NFe", "Senha do arquivo do certificado digital.");
        seedConfig("nfe_schema_path", nfeSchemaPath, "Caminho schemas NFe", "Diretório com os schemas oficiais da SEFAZ.");
        seedConfig("nfe_xml_path", nfeXmlPath, "Diretório XML NFe", "Local para armazenar XML gerados/retornados.");

        // Dados do emitente da NFe
        seedConfig("nfe_cnpj", "", "CNPJ emitente NFe", "CNPJ cadastrado para emissão de NFe.");
        seedConfig("nfe_razao_social", "", "Razão social NFe", "Razão social do emitente.");
        seedConfig("nfe_nome_fantasia", "", "Nome fantasia NFe", "Nome fantasia do emitente.");
        seedConfig("nfe_inscricao_estadual", "", "IE NFe", "Inscrição estadual do emitente.");
        seedConfig("nfe_inscricao_municipal", "", "IM NFe", "Inscrição municipal do emitente (se houver).");
        seedConfig("nfe_regime_tributario", "1", "Regime tributário NFe", "Regime tributário do emitente (CRT).");
        seedConfig("nfe_logradouro", "", "Logradouro NFe", "Logradouro do emitente.");
        seedConfig("nfe_numero", "", "Número NFe", "Número do endereço do emitente.");
        seedConfig("nfe_bairro", "", "Bairro NFe", "Bairro do emitente.");
        seedConfig("nfe_municipio", "", "Município NFe", "Município do emitente.");
        seedConfig("nfe_cod_municipio", "", "Código IBGE município NFe", "Código IBGE do município do emitente.");
        seedConfig("nfe_uf", "", "UF NFe", "UF do emitente.");
        seedConfig("nfe_cep", "", "CEP NFe", "CEP do emitente.");
        seedConfig("nfe_complemento", "", "Complemento NFe", "Complemento do endereço do emitente.");
        seedConfig("nfe_telefone", "", "Telefone NFe", "Telefone de contato do emitente.");
        seedConfig("nfe_email", "", "Email NFe", "Email do emitente.");
        seedConfig("nfe_email_contabilidade", "", "Email contabilidade NFe", "Email da contabilidade.");
        seedConfig("nfe_logo_path", "", "Logo DANFE (caminho)", "Caminho absoluto da imagem usada no DANFE.");
        seedConfig("nfe_logo_base64", "", "Logo DANFE (base64)", "Imagem em Base64 (data URI) usada no DANFE.");
        seedConfig("nfe_consumidor_cpf", "", "CPF consumidor NFe", "CPF padrão usado quando não há cliente identificado (validar em produção).");

        // CSC / QRCode da NFe
        seedConfig("nfe_id_csc", "", "ID CSC NFe", "Identificador do CSC para QRCode.");
        seedConfig("nfe_token_csc", "", "Token CSC NFe", "Token associado ao CSC.");

        // Configurações NFC-e
        seedConfig("nfce_ambiente", "2", "Ambiente NFC-e", "Ambiente de emissão NFC-e (1=Produção, 2=Homologação)");
        seedConfig("nfce_serie", "1", "Série NFC-e", "Número de série para emissão de NFC-e");
        seedConfig("nfce_imprimir_automatico", "true", "Imprimir NFC-e Automático", "Imprime DANFCE automaticamente após autorização");
        seedConfig("nfce_via_consumidor", "true", "Imprimir Via Consumidor", "Imprime via do consumidor do DANFCE");
        seedConfig("nfce_modo_impressao", "TERMICA", "Modo Impressão NFC-e", "Modo de impressão: TERMICA ou A4");
        seedConfig("nfce_largura_bobina", "80", "Largura Bobina mm", "Largura da bobina em milímetros (58, 80)");
        seedConfig("nfce_contingencia_ativa", "true", "Contingência NFCe Ativa", "Habilita modo de contingência offline");
        seedConfig("nfce_simular_sucesso_retransmissao", "true", "Simular Sucesso", "Simular sucesso na retransmissão (desenvolvimento)");
        seedConfig("nfce_id_csc", "", "ID CSC NFC-e", "Identificador do CSC para emissão de NFC-e.");
        seedConfig("nfce_token_csc", "", "Token CSC NFC-e", "Token de segurança associado ao CSC.");
        seedConfig("nfce_numero", "370375", "Número NFC-e", "Número sequencial da NFC-e.");
        seedConfig("nfce_processamento_fila_ativo", "true", "Processamento da fila NFC-e ativo", "Ativa o processamento da fila de emissão.");
        seedConfig("nfce_sincronizacao_batch_size", "20", "Batch de sincronização NFC-e", "Quantidade de itens por lote na sincronização.");
        seedConfig("nfce_sincronizacao_delay_ms", "2000", "Delay de sincronização NFC-e (ms)", "Intervalo entre execuções da sincronização (ms).");
        seedConfig("nfce_retry_interval", "60000", "Intervalo de retry NFC-e (ms)", "Intervalo em milissegundos entre novas tentativas.");
        seedConfig("nfce_max_tentativas", "5", "Máximo de tentativas NFC-e", "Quantidade máxima de tentativas de emissão.");
        seedConfig("nfce_dias_limpeza_fila", "30", "Dias para limpeza da fila NFC-e", "Dias para retenção de registros na fila.");
        seedConfig("nfce_log_contingencia", "true", "Log de contingência NFC-e", "Registra entradas da contingência offline.");
        seedConfig("nfce_notificar_sincronizacao", "true", "Notificar sincronização NFC-e", "Envia notificações após sincronização da fila.");
        seedConfig("nfce_via_estabelecimento", "false", "Imprimir via do estabelecimento", "Imprime via do estabelecimento no DANFCE.");
        seedConfig("nfce_url_consulta_sp", "https://www.nfce.fazenda.sp.gov.br/consulta", "URL consulta NFC-e SP", "URL de consulta de NFC-e para SP em produção.");
        seedConfig("nfce_url_consulta_homolog_sp", "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta", "URL consulta homologação NFC-e SP", "URL de consulta de NFC-e para SP em homologação.");

        // WhatsApp (microserviço)
        seedConfig("whatsapp_enabled", "true", "WhatsApp habilitado", "Habilita integração com o microserviço de WhatsApp");
        seedConfig("whatsapp_service_url", "", "URL do serviço WhatsApp", "Endpoint base do microserviço (ex.: http://host:3001)");
        seedConfig("whatsapp_max_attachment_mb", "10", "Tamanho máximo do anexo (MB)", "Limite de tamanho em MB para envio de anexos via WhatsApp");
        seedConfig("whatsapp_send_timeout_seconds", "15", "Timeout de envio (s)", "Tempo máximo (segundos) para aguardar resposta do microserviço");

        // Mercado Pago
        seedConfig("mercadopago_sandbox", "true", "Mercado Pago sandbox", "Define se as chamadas ao Mercado Pago usam sandbox (true) ou produção (false)");
        seedConfig("mercadopago_pix_timeout_minutes", "10", "Timeout PIX (minutos)", "Tempo em minutos para expiração de pagamentos via PIX no Mercado Pago");
        seedConfig("mercadopago_base_url", "https://api.mercadopago.com", "URL base Mercado Pago", "Endpoint base da API do Mercado Pago para produção");
        seedConfig("mercadopago_sandbox_base_url", "https://api.mercadopago.com", "URL sandbox Mercado Pago", "Endpoint base da API do Mercado Pago para sandbox");
        seedConfig("mercadopago_webhook_validate_signature", "false", "Validar assinatura de webhook", "Habilita a validação das assinaturas enviadas pelos webhooks do Mercado Pago");
        seedConfig("mercadopago_pix_max_amount", "50000.00", "Valor máximo PIX", "Valor máximo permitido para transações via PIX no Mercado Pago");
        seedConfig("mercadopago_timeout_connection", "30000", "Timeout de conexão", "Timeout em milissegundos para conexões REST com o Mercado Pago");
        seedConfig("mercadopago_timeout_read", "30000", "Timeout de leitura", "Timeout em milissegundos para leitura das respostas da API do Mercado Pago");
        seedConfig("mercadopago_access_token_production", "", "Access Token Mercado Pago (produção)", "Token usado para autenticar chamadas em produção");
        seedConfig("mercadopago_public_key_production", "", "Public Key Mercado Pago (produção)", "Chave pública usada pelo frontend em produção");
        seedConfig("mercadopago_access_token", "", "Access Token Mercado Pago", "Token de autenticação padrão (usa sandbox por default)");
        seedConfig("mercadopago_public_key", "", "Public Key Mercado Pago", "Chave pública usada no frontend para autenticação de sandbox");
        seedConfig("mercadopago_notification_url", "", "URL de notificações Mercado Pago", "Endpoint exposto para receber notificações do Mercado Pago (compatibilidade)");
        seedConfig("mercadopago_access_token_sandbox", "", "Access Token Sandbox", "Token de autenticação para ambiente de testes do Mercado Pago");
        seedConfig("mercadopago_public_key_sandbox", "", "Public Key Sandbox", "Chave pública usada em sandbox");
        seedConfig("mercadopago_webhook_secret", "", "Secret de webhook", "Secret usado para validar assinaturas recebidas via webhook");
        seedConfig("mercadopago_webhook_url", "", "URL do webhook", "URL pública usada para receber notificações de pagamentos do Mercado Pago");
        seedConfig("mercadopago_webhook_allow_payload_status", "false", "Webhook MP: aceitar status do payload (teste)", "Quando true, usa status presente no payload do webhook sem consultar o Mercado Pago (somente para testes).");

        // PagSeguro
        seedConfig("pagseguro_sandbox", "true", "PagSeguro sandbox", "Define se as chamadas ao PagSeguro usam sandbox (true) ou produção (false)");
        seedConfig("pagseguro_base_url", "https://api.pagseguro.com/", "URL base PagSeguro", "Endpoint base da API do PagSeguro para produção");
        seedConfig("pagseguro_sandbox_base_url", "https://sandbox.api.pagseguro.com/", "URL sandbox PagSeguro", "Endpoint base do PagSeguro para testes");
        seedConfig("pagseguro_timeout_connection", "30000", "Timeout de conexão PagSeguro", "Tempo máximo em milissegundos para abrir a conexão");
        seedConfig("pagseguro_timeout_read", "30000", "Timeout de leitura PagSeguro", "Tempo máximo em milissegundos para ler a resposta");
        seedConfig("pagseguro_pix_timeout_minutes", "10", "Timeout PIX PagSeguro (minutos)", "Tempo em minutos para expiração de pagamentos via PIX no PagSeguro.");
        seedConfig("pagseguro_email", "", "Email PagSeguro", "Email associado à conta PagSeguro");
        seedConfig("pagseguro_token", "", "Token PagSeguro", "Token de autenticação da conta PagSeguro");
        seedConfig("pagseguro_public_key", "", "Chave pública PagSeguro", "Chave pública usada para criptografar cartão no frontend (encryptedCard).");
        seedConfig("pagseguro_notification_url", "", "URL de notificações PagSeguro", "Endpoint público usado para receber notificações do PagSeguro");
        seedConfig("pagseguro_webhook_allow_payload_status", "false", "Webhook PagSeguro: aceitar status do payload (teste)", "Quando true, usa status presente no payload do webhook sem exigir charges completas (somente para testes).");

        // Uber Direct
        seedConfig("uber_client_id", "", "Uber Client ID", "Client ID da integracao Uber Direct.");
        seedConfig("uber_client_secret", "", "Uber Client Secret", "Client Secret da integracao Uber Direct.");
        seedConfig("uber_customer_id", "", "Uber Customer ID", "Customer ID da integracao Uber Direct.");
        seedConfig("uber_scope", "delivery", "Uber Scope", "Scope usado para autenticar na Uber (default: delivery).");
        seedConfig("uber_access_token", "", "Uber Access Token", "Token opcional para pular OAuth (sandbox/testes).");
        seedConfig("uber_token_url", "https://login.uber.com/oauth/v2/token", "Uber Token URL", "Endpoint OAuth da Uber.");
        seedConfig("uber_api_base_url", "https://api.uber.com", "Uber API Base URL", "Endpoint base da API Uber Direct.");
        seedConfig("uber_pickup_address", "", "Uber Pickup Address", "Endereco de retirada padrao (estabelecimento).");
        seedConfig("uber_pickup_name", "", "Uber Pickup Name", "Nome do ponto de retirada.");
        seedConfig("uber_pickup_phone", "", "Uber Pickup Phone", "Telefone do ponto de retirada.");
        seedConfig("uber_pickup_notes", "", "Uber Pickup Notes", "Observacoes do ponto de retirada.");
        seedConfig("uber_pickup_ready_path", "/v1/customers/%s/deliveries/%s", "Uber Pickup Ready Path", "Path para marcar pickup ready (customerId, deliveryId).");

        // Parcelamento por gateway (sem juros) — usando prefixos dos gateways
        // Mercado Pago
        seedConfig("mercadopago_installments_enabled", "true", "Mercado Pago: Parcelamento habilitado", "Habilita parcelamento sem juros no Mercado Pago.");
        seedConfig("mercadopago_installments_min_amount", "0.00", "Mercado Pago: Valor mínimo para parcelar (R$)", "Valor mínimo do pedido para permitir parcelar (R$).");
        seedConfig("mercadopago_installments_max_times", "3", "Mercado Pago: Máximo de parcelas", "Quantidade máxima de parcelas (sem juros) no Mercado Pago.");
        // PagSeguro
        seedConfig("pagseguro_installments_enabled", "true", "PagSeguro: Parcelamento habilitado", "Habilita parcelamento sem juros no PagSeguro.");
        seedConfig("pagseguro_installments_min_amount", "0.00", "PagSeguro: Valor mínimo para parcelar (R$)", "Valor mínimo do pedido para permitir parcelar (R$).");
        seedConfig("pagseguro_installments_max_times", "3", "PagSeguro: Máximo de parcelas", "Quantidade máxima de parcelas (sem juros) no PagSeguro.");

        // Gateway padrão
        seedConfig("payment_default_gateway", "MERCADOPAGO", "Gateway padrão de pagamento", "Gateway ativo para os pagamentos do delivery (MERCADOPAGO ou PAGSEGURO).");
        // Integração espresso_back (sync + notificações)
        seedConfig("espresso.sync.base-url", espressoSyncBaseUrl, "Espresso Sync Base URL",
                "Base URL do espresso_back para sync e notificações.");
        seedConfig("espresso.sync.api-key", websiteSyncApiKey, "Espresso Sync API Key",
                "Chave de autenticação para integração com espresso_back.");

        // Integração espresso (eventos)
        seedConfig("espresso-api.app.base-url", espressoApiBaseUrl, "Espresso API Base URL",
                "Base URL do sistema Espresso para eventos.");

        // Configurações de aniversário
        seedConfig("site_aniversario_ativo", "true", "Notificações de aniversário ativas", "Ativa/desativa o envio de mensagens de aniversário.");
        seedConfig("site_aniversario_dias_antes", "7", "Dias antes do aniversário", "Quantos dias antes enviar a mensagem de pré‑aniversário.");
        seedConfig("site_aniversario_msg_pre", "Seu aniversário está chegando! Será uma honra receber você para comemorar.", "Mensagem de pré-aniversário", "Mensagem enviada antes do aniversário do cliente.");
        seedConfig("site_aniversario_msg_dia", "Feliz aniversário! Desejamos um dia incrível para você.", "Mensagem de aniversário", "Mensagem enviada no dia do aniversário do cliente.");
        seedConfig("site_aniversario_voucher_valor", "0", "Valor do voucher (R$)", "0 = não oferece voucher.");
        seedConfig("site_aniversario_voucher_msg", "Você terá um voucher de R$ {valor} disponível para celebrar.", "Mensagem do voucher", "Mensagem opcional com informações sobre o voucher.");

        // Configurações de eventos
        seedConfig("site_evento_ativo", "true", "Notificações de eventos ativas", "Ativa/desativa o envio de mensagens de eventos.");
        seedConfig("site_evento_dias_antes", "7", "Dias antes do evento", "Quantos dias antes enviar a mensagem de pré‑evento.");
        seedConfig("site_evento_msg_pre", "Vem aí: {evento}. Esperamos você para esse momento especial!", "Mensagem de pré-evento", "Mensagem enviada antes do evento.");
        seedConfig("site_evento_msg_day", "Hoje tem {evento}! Será um prazer receber você.", "Mensagem do dia do evento", "Mensagem enviada no dia do evento.");
        seedConfig("site_evento_deeplink", "/areacliente/eventos", "Deeplink de eventos", "Rota do app para a lista de eventos.");

        // Configurações de validade
        seedConfig("validade_alertar_vencimento_percentual", "20", "Alertar vencimento percentual", "Percentual da vida útil restante para alertar sobre vencimento (padrão 20%)");

        // Configurações do Print Agent
        seedConfig("print_agent_url", "", "URL do Print Agent", "URL do servidor de impressão para integração com impressoras.");
        seedConfig("print_agent_agent_id", "", "ID do Print Agent", "Identificador único utilizado pelo agente para se conectar ao ERP.");
        seedConfig("print_agent_erp_url", "", "URL WebSocket do ERP", "Endpoint WebSocket que o Print Agent deve escutar para receber jobs.");

        log.info("=============================================");
        log.info("FINALIZADO SEED DE CONFIGURAÇÕES");
        log.info("=============================================");
    }

    private void seedConfig(String chave, String valorPadrao, String nome, String descricao) {
        Configuracao existente = configuracaoRepository.findByChave(chave).orElse(null);

        if (existente == null) {
            log.info("SEED: Criando configuracao ausente '{}'", chave);
            Configuracao config = Configuracao.builder()
                    .chave(chave)
                    .valor(valorPadrao)
                    .nome(nome)
                    .descricao(descricao)
                    .build();
            configuracaoRepository.save(config);
        } else {
            log.debug("SEED: Configuracao existente preservada '{}'", chave);
        }
    }

    private void seedTipoReceita(String nome) {
        try {
            boolean existe = tipoReceitaRepository.existsByNomeIgnoreCase(nome);
            if (!existe) {
                syncTipoReceitaSequence();
                log.info("SEED: Criando tipo de receita '{}'", nome);
                TipoReceita tr = TipoReceita.builder().nome(nome).build();
                tipoReceitaRepository.save(tr);
            } else {
                log.debug("SEED: Tipo de receita '{}' já existe.", nome);
            }
        } catch (Exception e) {
            log.warn("SEED: Falha ao criar tipo de receita '{}': {}", nome, e.getMessage());
        }
    }

    private void syncTipoReceitaSequence() {
        try {
            entityManager.createNativeQuery("""
                SELECT setval(
                    pg_get_serial_sequence('tipo_receita','id'),
                    (SELECT COALESCE(MAX(id),0) FROM tipo_receita)
                )
                """).getSingleResult();
        } catch (Exception e) {
            log.warn("SEED: Não foi possível sincronizar sequência de tipo_receita: {}", e.getMessage());
        }
    }
}

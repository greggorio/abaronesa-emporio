package com.baronesa.emporio.util;

import com.baronesa.emporio.entity.Configuracao;
import com.baronesa.emporio.repository.ConfiguracaoRepository;
import com.baronesa.emporio.constants.NfeConstants;
import com.baronesa.emporio.enums.TipoDocumentoFiscal;
import com.baronesa.emporio.enums.ModoImpressaoNFCe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Gerenciador de configurações que busca valores na tabela 'configuracoes'.
 * Implementa cache para otimizar acesso frequente às configurações.
 */
@Slf4j
@Component
public class ConfigManager {

    private static final long CACHE_TIMEOUT = TimeUnit.MINUTES.toMillis(5); // Cache expira em 5 minutos

    @Autowired
    private ConfiguracaoRepository configuracaoRepository;

    private final Map<String, CachedConfig> configCache = new HashMap<>();

    /**
     * Obtém o valor de uma configuração pelo nome da chave.
     * Se a configuração não existir, retorna o valor padrão fornecido.
     *
     * @param chave Nome da chave de configuração
     * @param valorPadrao Valor padrão caso a configuração não exista
     * @return Valor da configuração
     */
    public String getConfig(String chave, String valorPadrao) {
        CachedConfig cachedConfig = configCache.get(chave);
        long currentTime = System.currentTimeMillis();

        // Se o cache existir e não expirou, use-o
        if (cachedConfig != null && (currentTime - cachedConfig.timestamp < CACHE_TIMEOUT)) {
            return cachedConfig.valor;
        }

        String valor;
        try {
            // Busca no repositório de configurações
            Optional<Configuracao> configOpt = configuracaoRepository.findByChave(chave);

            if (configOpt.isPresent()) {
                valor = configOpt.get().getValor();
            } else {
                valor = valorPadrao;
            }

        } catch (Exception e) {
            log.warn("Erro ao buscar configuração '{}': {}. Usando valor padrão: {}",
                    chave, e.getMessage(), valorPadrao);
            valor = valorPadrao;
        }

        // Atualiza o cache
        configCache.put(chave, new CachedConfig(valor, currentTime));
        return valor;
    }

    /**
     * Obtém uma configuração como valor booleano.
     *
     * @param chave Nome da chave
     * @param valorPadrao Valor padrão
     * @return Valor booleano da configuração
     */
    public boolean getBooleanConfig(String chave, boolean valorPadrao) {
        String valor = getConfig(chave, String.valueOf(valorPadrao));
        return "true".equalsIgnoreCase(valor) || "1".equals(valor) || "sim".equalsIgnoreCase(valor);
    }

    /**
     * Obtém uma configuração como valor inteiro.
     *
     * @param chave Nome da chave
     * @param valorPadrao Valor padrão
     * @return Valor inteiro da configuração
     */
    public int getIntConfig(String chave, int valorPadrao) {
        String valor = getConfig(chave, String.valueOf(valorPadrao));
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            log.warn("Valor não numérico para chave '{}': {}. Usando padrão: {}",
                    chave, valor, valorPadrao);
            return valorPadrao;
        }
    }

    /**
     * Obtém uma configuração como valor double.
     *
     * @param chave Nome da chave
     * @param valorPadrao Valor padrão
     * @return Valor double da configuração
     */
    public double getDoubleConfig(String chave, double valorPadrao) {
        String valor = getConfig(chave, String.valueOf(valorPadrao));
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            log.warn("Valor não numérico para chave '{}': {}. Usando padrão: {}",
                    chave, valor, valorPadrao);
            return valorPadrao;
        }
    }

    /**
     * Atualiza uma configuração na tabela configuracoes.
     *
     * @param chave Nome da chave
     * @param valor Novo valor
     * @return true se atualizado com sucesso, false caso contrário
     */
    public boolean setConfig(String chave, String valor) {
        try {
            Optional<Configuracao> configOpt = configuracaoRepository.findByChave(chave);
            Configuracao config;

            if (configOpt.isPresent()) {
                // Atualiza configuração existente
                config = configOpt.get();
                config.setValor(valor);
            } else {
                // Cria nova configuração
                config = Configuracao.builder()
                        .chave(chave)
                        .valor(valor)
                        .nome(chave.replace("_", " "))
                        .descricao("Configuração criada automaticamente")
                        .build();
            }

            // Salva a configuração
            configuracaoRepository.save(config);

            // Atualiza o cache
            configCache.put(chave, new CachedConfig(valor, System.currentTimeMillis()));
            return true;

        } catch (Exception e) {
            log.error("Erro ao atualizar configuração '{}': {}", chave, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Remove uma configuração do cache e do banco
     *
     * @param chave Nome da chave
     * @return true se removido com sucesso, false caso contrário
     */
    public boolean removeConfig(String chave) {
        try {
            Optional<Configuracao> configOpt = configuracaoRepository.findByChave(chave);
            if (configOpt.isPresent()) {
                configuracaoRepository.delete(configOpt.get());
                configCache.remove(chave);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Erro ao remover configuração '{}': {}", chave, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifica se uma configuração existe
     *
     * @param chave Nome da chave
     * @return true se existe, false caso contrário
     */
    public boolean existsConfig(String chave) {
        try {
            return configuracaoRepository.existsByChave(chave);
        } catch (Exception e) {
            log.error("Erro ao verificar existência da configuração '{}': {}", chave, e.getMessage(), e);
            return false;
        }
    }

    public boolean isGamificacaoAtiva() {
        return getBooleanConfig("gamificacao_ativo", true);
    }

    public BigDecimal getGamificacaoValorPara1Ponto() {
        String valor = getConfig("gamificacao_valor_para_1_ponto", "5.00");
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            log.warn("Valor inválido para gamificacao_valor_para_1_ponto: {}. Usando 5.00", valor);
            return new BigDecimal("5.00");
        }
    }

    public RoundingMode getGamificacaoArredondamento() {
        String value = getConfig("gamificacao_arredondamento", "FLOOR");
        if (value == null) value = "FLOOR";
        switch (value.trim().toUpperCase()) {
            case "CEIL":
                return RoundingMode.CEILING;
            case "ROUND":
                return RoundingMode.HALF_UP;
            default:
                return RoundingMode.FLOOR;
        }
    }

    public Integer getGamificacaoExpiracaoEmDias() {
        return getIntConfig("gamificacao_expiracao_pontos_em_dias", 0);
    }

    /**
     * Obtém o percentual de alerta de vencimento para produtos com controle de validade
     * @return Percentual (padrão 20)
     */
    public int getValidadeAlertarVencimentoPercentual() {
        return getIntConfig("validade_alertar_vencimento_percentual", 20);
    }

    /**
     * Obtém o número sequencial para o tipo de documento
     * @param tipo Tipo de documento (NFE ou NFCE)
     * @return Próximo número disponível
     */
    public int getProximoNumero(TipoDocumentoFiscal tipo) {
        String chave = tipo.isNFe() ? NfeConstants.NFE_NUMERO : NfeConstants.NFCE_NUMERO;
        return getIntConfig(chave, 1);
    }

    /**
     * Obtém a série para o tipo de documento
     * @param tipo Tipo de documento (NFE ou NFCE)
     * @return Série configurada
     */
    public String getSerie(TipoDocumentoFiscal tipo) {
        String chave = tipo.isNFe() ? NfeConstants.NFE_SERIE : NfeConstants.NFCE_SERIE;
        return getConfig(chave, NfeConstants.DEFAULT_SERIE);
    }

    /**
     * Obtém o ambiente para o tipo de documento
     * @param tipo Tipo de documento (NFE ou NFCE)
     * @return 1=Produção, 2=Homologação
     */
    public int getAmbiente(TipoDocumentoFiscal tipo) {
        String chave = tipo.isNFe() ? NfeConstants.NFE_AMBIENTE : NfeConstants.NFCE_AMBIENTE;
        return getIntConfig(chave, Integer.parseInt(NfeConstants.DEFAULT_AMBIENTE));
    }

    /**
     * Verifica se deve imprimir automaticamente o DANFCE
     * @return true se deve imprimir automaticamente
     */
    public boolean isImprimirNFCeAutomatico() {
        return getBooleanConfig(NfeConstants.NFCE_IMPRIMIR_AUTOMATICO, true);
    }

    /**
     * Obtém o modo de impressão configurado para NFCe
     * @return ModoImpressaoNFCe configurado
     */
    public ModoImpressaoNFCe getModoImpressaoNFCe() {
        String modo = getConfig(NfeConstants.NFCE_MODO_IMPRESSAO, NfeConstants.DEFAULT_MODO_IMPRESSAO);
        return ModoImpressaoNFCe.fromString(modo);
    }

    /**
     * Obtém a largura da bobina configurada
     * @return largura em milímetros
     */
    public int getLarguraBobinaNFCe() {
        return getIntConfig(NfeConstants.NFCE_LARGURA_BOBINA,
                Integer.parseInt(NfeConstants.DEFAULT_LARGURA_BOBINA));
    }

    /**
     * Obtém a URL de consulta NFCe para o estado
     * @param uf Sigla do estado
     * @param homologacao true para ambiente de homologação
     * @return URL de consulta
     */
    public String getUrlConsultaNFCe(String uf, boolean homologacao) {
        String chave = homologacao ?
                NfeConstants.NFCE_URL_CONSULTA_HOMOLOG_PREFIX + uf.toLowerCase() :
                NfeConstants.NFCE_URL_CONSULTA_PREFIX + uf.toLowerCase();

        // URLs padrão para SP se não configurado
        String urlPadrao = homologacao ?
                "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta" :
                "https://www.nfce.fazenda.sp.gov.br/consulta";

        return getConfig(chave, urlPadrao);
    }

    /**
     * Obtém o token CSC para NFCe
     * @return Token CSC
     */
    public String getTokenCSC() {
        return getConfig(NfeConstants.NFCE_TOKEN_CSC, "");
    }

    /**
     * Obtém o ID CSC para NFCe
     * @return ID CSC
     */
    public String getIdCSC() {
        return getConfig(NfeConstants.NFCE_ID_CSC, "");
    }

    /**
     * Incrementa e retorna o próximo número para o tipo de documento
     * @param tipo Tipo de documento
     * @param numeroAtual Número atual usado
     * @return próximo número disponível
     */
    public int incrementarNumero(TipoDocumentoFiscal tipo, int numeroAtual) {
        String chave = tipo.isNFe() ? NfeConstants.NFE_NUMERO : NfeConstants.NFCE_NUMERO;
        int proximoNumero = numeroAtual + 1;
        setConfig(chave, String.valueOf(proximoNumero));
        return proximoNumero;
    }

    /**
     * Valida se as configurações mínimas para NFCe estão presentes
     * @return true se todas as configurações obrigatórias estão presentes
     */
    public boolean isNFCeConfigurado() {
        String tokenCSC = getTokenCSC();
        String idCSC = getIdCSC();

        return tokenCSC != null && !tokenCSC.isEmpty() &&
                idCSC != null && !idCSC.isEmpty();
    }

    /**
     * Limpa o cache de configurações
     */
    public void clearCache() {
        configCache.clear();
        log.info("Cache de configurações limpo");
    }

    /**
     * Limpa o cache de uma configuração específica
     *
     * @param chave Nome da chave
     */
    public void clearCacheForKey(String chave) {
        configCache.remove(chave);
        log.debug("Cache limpo para chave: {}", chave);
    }

    /**
     * Classe interna para armazenar valores em cache com timestamp
     */
    private static class CachedConfig {
        private final String valor;
        private final long timestamp;

        public CachedConfig(String valor, long timestamp) {
            this.valor = valor;
            this.timestamp = timestamp;
        }
    }
}
